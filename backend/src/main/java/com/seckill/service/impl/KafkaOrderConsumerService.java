package com.seckill.service.impl;

import com.seckill.datasource.DS;
import com.seckill.datasource.DataSourceType;
import com.seckill.mapper.OrderMapper;
import com.seckill.mapper.SeckillProductMapper;
import com.seckill.model.dto.SeckillOrderMessage;
import com.seckill.model.entity.Order;
import com.seckill.model.entity.SeckillProduct;
import com.seckill.utils.SnowflakeIdGenerator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Kafka 秒杀订单消费者 —— 削峰填谷，异步落库
 * <p>
 * 流程：
 * 1. 消费 Kafka 消息
 * 2. 幂等校验（DB 查是否已存在该用户+商品的秒杀订单）
 * 3. MySQL 乐观锁扣减真实库存
 * 4. 插入订单记录
 * 5. 手动 ACK
 */
@Service
public class KafkaOrderConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderConsumerService.class);

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";

    private final OrderMapper          orderMapper;
    private final SeckillProductMapper seckillProductMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final StringRedisTemplate  stringRedisTemplate;

    public KafkaOrderConsumerService(OrderMapper orderMapper,
                                     SeckillProductMapper seckillProductMapper,
                                     SnowflakeIdGenerator snowflakeIdGenerator,
                                     StringRedisTemplate stringRedisTemplate) {
        this.orderMapper          = orderMapper;
        this.seckillProductMapper = seckillProductMapper;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.stringRedisTemplate  = stringRedisTemplate;
    }

    @KafkaListener(topics = "seckill-order", groupId = "seckill-order-group")
    @DS(DataSourceType.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void handleSeckillOrder(ConsumerRecord<String, SeckillOrderMessage> record,
                                   Acknowledgment acknowledgment) {
        SeckillOrderMessage msg = record.value();
        log.info("Kafka 消费秒杀订单消息: userId={} spId={} messageId={} partition={} offset={}",
                msg.getUserId(), msg.getSeckillProductId(), msg.getMessageId(),
                record.partition(), record.offset());

        try {
            // 1. 幂等校验：DB 中是否已有该用户+商品的未取消秒杀订单
            int count = orderMapper.countSeckillOrder(msg.getUserId(), msg.getSeckillProductId());
            if (count > 0) {
                log.warn("重复消费：用户 {} 商品 {} 已存在秒杀订单，跳过", msg.getUserId(), msg.getSeckillProductId());
                acknowledgment.acknowledge();
                return;
            }

            // 2. MySQL 乐观锁扣减真实库存
            SeckillProduct sp = seckillProductMapper.findById(msg.getSeckillProductId());
            if (sp == null || sp.getAvailStock() < msg.getQuantity()) {
                log.error("库存不足或商品不存在 spId={}，回滚 Redis 库存", msg.getSeckillProductId());
                rollbackRedisStock(msg.getSeckillProductId(), msg.getQuantity());
                acknowledgment.acknowledge();
                return;
            }

            int updated = seckillProductMapper.decreaseStock(
                    msg.getSeckillProductId(), msg.getQuantity(), sp.getVersion());
            if (updated == 0) {
                log.warn("乐观锁冲突 spId={} version={}，回滚 Redis 库存", msg.getSeckillProductId(), sp.getVersion());
                rollbackRedisStock(msg.getSeckillProductId(), msg.getQuantity());
                // 不 ACK，让 Kafka 重试
                return;
            }

            // 3. 生成订单（雪花算法 orderNo）
            Order order = new Order();
            order.setOrderNo(String.valueOf(snowflakeIdGenerator.nextId()));
            order.setUserId(msg.getUserId());
            order.setProductId(msg.getSeckillProductId());
            order.setProductType(1);
            order.setProductName(msg.getProductName());
            order.setQuantity(msg.getQuantity());
            order.setUnitPrice(msg.getSeckillPrice());
            order.setAmount(msg.getSeckillPrice().multiply(BigDecimal.valueOf(msg.getQuantity())));
            order.setStatus(0);
            orderMapper.insert(order);

            log.info("秒杀订单创建成功 userId={} spId={} orderNo={}",
                    msg.getUserId(), msg.getSeckillProductId(), order.getOrderNo());

            // 4. 手动 ACK
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("处理秒杀订单消息异常 messageId={}, error={}", msg.getMessageId(), e.getMessage(), e);
            // 不 ACK，Kafka 会自动重试
        }
    }

    /**
     * Redis 库存回滚（当 DB 扣减失败时）
     */
    private void rollbackRedisStock(Long seckillProductId, int quantity) {
        String stockKey = STOCK_KEY_PREFIX + seckillProductId;
        stringRedisTemplate.opsForValue().increment(stockKey, quantity);
    }
}
