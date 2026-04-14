package com.seckill.stock.service.impl;

import com.seckill.stock.datasource.DS;
import com.seckill.stock.datasource.DataSourceType;
import com.seckill.stock.mapper.SeckillProductMapper;
import com.seckill.stock.model.dto.SeckillOrderMessage;
import com.seckill.stock.model.dto.SeckillRequestDTO;
import com.seckill.stock.model.entity.Order;
import com.seckill.stock.model.entity.SeckillProduct;
import com.seckill.stock.model.vo.ResultVO;
import com.seckill.stock.service.KafkaProducerService;
import com.seckill.stock.service.SeckillService;
import com.seckill.stock.utils.RedisUtils;
import com.seckill.stock.utils.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class StockSeckillServiceImpl implements SeckillService {

    private static final Logger log = LoggerFactory.getLogger(StockSeckillServiceImpl.class);
    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String USER_SECKILL_KEY = "seckill:user:%d:sp:%d";

    private final RedisUtils redisUtils;
    private final SeckillProductMapper seckillProductMapper;
    private final KafkaProducerService kafkaProducerService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final RestTemplate restTemplate;

    @Value("${order-service.url}")
    private String orderServiceUrl;

    public StockSeckillServiceImpl(RedisUtils redisUtils,
                                    SeckillProductMapper seckillProductMapper,
                                    KafkaProducerService kafkaProducerService,
                                    SnowflakeIdGenerator snowflakeIdGenerator,
                                    RestTemplate restTemplate) {
        this.redisUtils = redisUtils;
        this.seckillProductMapper = seckillProductMapper;
        this.kafkaProducerService = kafkaProducerService;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.restTemplate = restTemplate;
    }

    @Override
    public Map<String, Object> doSeckill(Long userId, SeckillRequestDTO dto) {
        Long spId = dto.getSeckillProductId();
        int quantity = dto.getQuantity();

        log.info("秒杀请求 userId={} spId={}", userId, spId);

        SeckillProduct sp = seckillProductMapper.findById(spId);
        if (sp == null || sp.getStatus() != 1) {
            throw new RuntimeException("秒杀商品不存在或已下架");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(sp.getStartTime())) {
            throw new RuntimeException("秒杀活动尚未开始");
        }
        if (now.isAfter(sp.getEndTime())) {
            throw new RuntimeException("秒杀活动已结束");
        }

        // Redis 预减库存
        String stockKey = STOCK_KEY_PREFIX + spId;
        if (Boolean.FALSE.equals(redisUtils.hasKey(stockKey))) {
            redisUtils.set(stockKey, String.valueOf(sp.getAvailStock()), 24, TimeUnit.HOURS);
        }
        Long remaining = redisUtils.decrement(stockKey);
        if (remaining < 0) {
            redisUtils.increment(stockKey);
            throw new RuntimeException("秒杀失败：库存不足");
        }

        // Redis 防重复下单
        String userKey = String.format(USER_SECKILL_KEY, userId, spId);
        Boolean isFirst = redisUtils.setIfAbsent(userKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isFirst)) {
            redisUtils.increment(stockKey);
            throw new RuntimeException("每人每件秒杀商品限购1次");
        }

        // 发送 Kafka 消息
        long messageId = snowflakeIdGenerator.nextId();
        SeckillOrderMessage msg = new SeckillOrderMessage(
                messageId, userId, spId, quantity,
                sp.getName(), sp.getSeckillPrice()
        );
        kafkaProducerService.sendSeckillOrder(msg);

        log.info("秒杀排队成功 userId={} spId={} messageId={}", userId, spId, messageId);

        Map<String, Object> result = new HashMap<>();
        result.put("messageId", messageId);
        result.put("status", "PROCESSING");
        result.put("message", "秒杀请求已提交，订单处理中...");
        return result;
    }

    @Override
    @DS(DataSourceType.MASTER)
    public Order getSeckillOrder(Long userId, Long seckillProductId) {
        // 通过 HTTP 调用 Order Service 查询秒杀订单
        String url = orderServiceUrl + "/api/order/internal/user-product?userId=" + userId + "&productId=" + seckillProductId;
        ResultVO result = restTemplate.getForObject(url, ResultVO.class);
        if (result != null && result.getData() != null) {
            // RestTemplate 会将 JSON 反序列化为 LinkedHashMap，需要手动转换
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            om.findAndRegisterModules();
            return om.convertValue(result.getData(), Order.class);
        }
        return null;
    }
}
