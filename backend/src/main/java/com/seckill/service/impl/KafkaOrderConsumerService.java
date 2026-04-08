package com.seckill.service.impl;

import com.seckill.datasource.DS;
import com.seckill.datasource.DataSourceType;
import com.seckill.mapper.OrderMapper;
import com.seckill.model.dto.SeckillOrderMessage;
import com.seckill.model.entity.Order;
import com.seckill.service.tcc.TccTransactionCoordinator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Kafka 秒杀订单消费者 —— TCC Try 阶段
 *
 * <p>消费 Kafka 消息，调用 TCC 协调器执行 Try 阶段：
 * <ol>
 *   <li>幂等校验（DB 层）</li>
 *   <li>TCC Try：库存预留 + 创建 TRYING 临时订单</li>
 *   <li>失败则回滚 Redis 库存</li>
 * </ol>
 */
@Service
public class KafkaOrderConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderConsumerService.class);

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";

    private final OrderMapper orderMapper;
    private final TccTransactionCoordinator tccCoordinator;
    private final StringRedisTemplate stringRedisTemplate;

    public KafkaOrderConsumerService(OrderMapper orderMapper,
                                     TccTransactionCoordinator tccCoordinator,
                                     StringRedisTemplate stringRedisTemplate) {
        this.orderMapper = orderMapper;
        this.tccCoordinator = tccCoordinator;
        this.stringRedisTemplate = stringRedisTemplate;
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
            // 1. 幂等校验：DB 中是否已有该用户+商品的有效订单
            int count = orderMapper.countSeckillOrder(msg.getUserId(), msg.getSeckillProductId());
            if (count > 0) {
                log.warn("重复消费：用户 {} 商品 {} 已存在有效订单，跳过",
                        msg.getUserId(), msg.getSeckillProductId());
                acknowledgment.acknowledge();
                return;
            }

            // 2. TCC Try 阶段：库存预留 + 创建 TRYING 临时订单
            Order order = tccCoordinator.tryPhase(
                    msg.getUserId(),
                    msg.getSeckillProductId(),
                    msg.getQuantity(),
                    msg.getProductName(),
                    msg.getSeckillPrice());

            if (order == null) {
                // Try 失败，回滚 Redis 预扣库存
                rollbackRedisStock(msg.getSeckillProductId(), msg.getQuantity());
                acknowledgment.acknowledge();
                return;
            }

            log.info("TCC Try 完成 userId={} spId={} orderNo={}",
                    msg.getUserId(), msg.getSeckillProductId(), order.getOrderNo());
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("处理秒杀订单消息异常 messageId={}, error={}", msg.getMessageId(), e.getMessage(), e);
            // 不 ACK，Kafka 会自动重试
        }
    }

    /**
     * Redis 库存回滚（当 TCC Try 失败时）
     */
    private void rollbackRedisStock(Long seckillProductId, int quantity) {
        String stockKey = STOCK_KEY_PREFIX + seckillProductId;
        stringRedisTemplate.opsForValue().increment(stockKey, quantity);
        log.info("Redis库存已回滚 spId={} quantity={}", seckillProductId, quantity);
    }
}
