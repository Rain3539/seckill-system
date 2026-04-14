package com.seckill.stock.service.impl;

import com.seckill.stock.datasource.DS;
import com.seckill.stock.datasource.DataSourceType;
import com.seckill.stock.mapper.SeckillProductMapper;
import com.seckill.stock.model.dto.SeckillOrderMessage;
import com.seckill.stock.model.vo.ResultVO;
import com.seckill.stock.tcc.TccStockParticipant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class KafkaStockConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaStockConsumerService.class);
    private static final String STOCK_KEY_PREFIX = "seckill:stock:";

    private final TccStockParticipant stockParticipant;
    private final SeckillProductMapper seckillProductMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RestTemplate restTemplate;

    @Value("${order-service.url}")
    private String orderServiceUrl;

    public KafkaStockConsumerService(TccStockParticipant stockParticipant,
                                      SeckillProductMapper seckillProductMapper,
                                      StringRedisTemplate stringRedisTemplate,
                                      RestTemplate restTemplate) {
        this.stockParticipant = stockParticipant;
        this.seckillProductMapper = seckillProductMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.restTemplate = restTemplate;
    }

    @KafkaListener(topics = "seckill-order", groupId = "seckill-order-group")
    @DS(DataSourceType.MASTER)
    public void handleSeckillOrder(ConsumerRecord<String, SeckillOrderMessage> record,
                                   Acknowledgment acknowledgment) {
        SeckillOrderMessage msg = record.value();
        log.info("Kafka 消费秒杀订单消息: userId={} spId={} messageId={} partition={} offset={}",
                msg.getUserId(), msg.getSeckillProductId(), msg.getMessageId(),
                record.partition(), record.offset());

        try {
            // 1. 幂等校验：通过 HTTP 调 Order Service
            String countUrl = orderServiceUrl + "/api/order/internal/count?userId=" + msg.getUserId() + "&productId=" + msg.getSeckillProductId();
            ResultVO countResult = restTemplate.getForObject(countUrl, ResultVO.class);
            if (countResult != null && countResult.getData() != null) {
                int count = ((Number) countResult.getData()).intValue();
                if (count > 0) {
                    log.warn("重复消费：用户 {} 商品 {} 已存在有效订单，跳过",
                            msg.getUserId(), msg.getSeckillProductId());
                    acknowledgment.acknowledge();
                    return;
                }
            }

            // 2. 本地库存 TCC Try（stock_db 事务）
            boolean reserved = stockParticipant.tryReserve(msg.getSeckillProductId(), msg.getQuantity());
            if (!reserved) {
                rollbackRedisStock(msg.getSeckillProductId(), msg.getQuantity());
                acknowledgment.acknowledge();
                return;
            }

            // 3. 远程订单 TCC Try（HTTP 调 Order Service）
            try {
                String orderTryUrl = orderServiceUrl + "/api/order/internal/create";
                Map<String, Object> body = new HashMap<>();
                body.put("userId", msg.getUserId());
                body.put("productId", msg.getSeckillProductId());
                body.put("quantity", msg.getQuantity());
                body.put("productName", msg.getProductName());
                body.put("unitPrice", msg.getSeckillPrice());

                ResultVO orderResult = restTemplate.postForObject(orderTryUrl, body, ResultVO.class);
                if (orderResult == null || orderResult.getCode() != 200) {
                    // 订单创建失败，取消本地库存预留
                    stockParticipant.cancel(msg.getSeckillProductId(), msg.getQuantity());
                    rollbackRedisStock(msg.getSeckillProductId(), msg.getQuantity());
                    acknowledgment.acknowledge();
                    return;
                }
            } catch (Exception e) {
                // HTTP 调用失败，取消本地库存预留
                log.error("调用订单服务创建订单失败 userId={} spId={} error={}",
                        msg.getUserId(), msg.getSeckillProductId(), e.getMessage());
                stockParticipant.cancel(msg.getSeckillProductId(), msg.getQuantity());
                rollbackRedisStock(msg.getSeckillProductId(), msg.getQuantity());
                // 不 ACK，让 Kafka 重试
                return;
            }

            log.info("TCC Try 完成 userId={} spId={}", msg.getUserId(), msg.getSeckillProductId());
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("处理秒杀订单消息异常 messageId={}, error={}", msg.getMessageId(), e.getMessage(), e);
        }
    }

    private void rollbackRedisStock(Long seckillProductId, int quantity) {
        String stockKey = STOCK_KEY_PREFIX + seckillProductId;
        stringRedisTemplate.opsForValue().increment(stockKey, quantity);
        log.info("Redis库存已回滚 spId={} quantity={}", seckillProductId, quantity);
    }
}
