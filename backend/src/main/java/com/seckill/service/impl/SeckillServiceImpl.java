package com.seckill.service.impl;

import com.seckill.datasource.DS;
import com.seckill.datasource.DataSourceType;
import com.seckill.mapper.OrderMapper;
import com.seckill.mapper.SeckillProductMapper;
import com.seckill.model.dto.SeckillOrderMessage;
import com.seckill.model.dto.SeckillRequestDTO;
import com.seckill.model.entity.Order;
import com.seckill.model.entity.SeckillProduct;
import com.seckill.service.KafkaProducerService;
import com.seckill.service.SeckillService;
import com.seckill.utils.RedisUtils;
import com.seckill.utils.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillServiceImpl implements SeckillService {

    private static final Logger log = LoggerFactory.getLogger(SeckillServiceImpl.class);

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String USER_SECKILL_KEY = "seckill:user:%d:sp:%d";

    private final RedisUtils             redisUtils;
    private final SeckillProductMapper   seckillProductMapper;
    private final OrderMapper            orderMapper;
    private final KafkaProducerService   kafkaProducerService;
    private final SnowflakeIdGenerator   snowflakeIdGenerator;

    public SeckillServiceImpl(RedisUtils redisUtils,
                              SeckillProductMapper seckillProductMapper,
                              OrderMapper orderMapper,
                              KafkaProducerService kafkaProducerService,
                              SnowflakeIdGenerator snowflakeIdGenerator) {
        this.redisUtils             = redisUtils;
        this.seckillProductMapper   = seckillProductMapper;
        this.orderMapper            = orderMapper;
        this.kafkaProducerService   = kafkaProducerService;
        this.snowflakeIdGenerator   = snowflakeIdGenerator;
    }

    /**
     * 秒杀下单（异步）：Redis 预减库存 → Kafka 发送消息 → 立即返回
     *
     * 防超卖策略：
     *   1. Redis DECR 原子预减库存 —— 挡住 99% 无效请求
     *   2. Redis SETNX 防重复下单 —— 同一用户同一商品只能秒杀一次（幂等）
     *   3. Kafka 异步 → MySQL 乐观锁最终扣减 —— 保证数据一致性
     *
     * 防重复策略（双重保障）：
     *   - Redis 层：seckill:user:{uid}:sp:{sid} SETNX 标记
     *   - DB 层：消费时 countSeckillOrder 再次校验
     */
    @Override
    public Map<String, Object> doSeckill(Long userId, SeckillRequestDTO dto) {
        Long spId     = dto.getSeckillProductId();
        int  quantity = dto.getQuantity();

        log.info("秒杀请求 userId={} spId={}", userId, spId);

        // ── 前置校验（从库即可，仅读）─────────────────────────────
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

        // ── Step 1: Redis 预减库存 ──────────────────────────────
        String stockKey = STOCK_KEY_PREFIX + spId;
        if (Boolean.FALSE.equals(redisUtils.hasKey(stockKey))) {
            // 缓存不存在，重新加载（兼容重启场景）
            redisUtils.set(stockKey, String.valueOf(sp.getAvailStock()), 24, TimeUnit.HOURS);
        }
        Long remaining = redisUtils.decrement(stockKey);
        if (remaining < 0) {
            redisUtils.increment(stockKey);
            throw new RuntimeException("秒杀失败：库存不足");
        }

        // ── Step 2: Redis 防重复下单（幂等）─────────────────────
        String  userKey = String.format(USER_SECKILL_KEY, userId, spId);
        Boolean isFirst = redisUtils.setIfAbsent(userKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isFirst)) {
            redisUtils.increment(stockKey);
            throw new RuntimeException("每人每件秒杀商品限购1次");
        }

        // ── Step 3: 发送 Kafka 消息，异步创建订单 ─────────────
        long messageId = snowflakeIdGenerator.nextId();
        SeckillOrderMessage msg = new SeckillOrderMessage(
                messageId, userId, spId, quantity,
                sp.getName(), sp.getSeckillPrice()
        );
        kafkaProducerService.sendSeckillOrder(msg);

        log.info("秒杀排队成功 userId={} spId={} messageId={}", userId, spId, messageId);

        // ── 立即返回排队结果 ──────────────────────────────────
        Map<String, Object> result = new HashMap<>();
        result.put("messageId", messageId);
        result.put("status", "PROCESSING");
        result.put("message", "秒杀请求已提交，订单处理中...");
        return result;
    }

    /**
     * 查询秒杀订单结果
     */
    @Override
    @DS(DataSourceType.MASTER)
    public Order getSeckillOrder(Long userId, Long seckillProductId) {
        return orderMapper.findByUserIdAndProductId(userId, seckillProductId);
    }
}
