package com.seckill.order.tcc;

import com.seckill.order.mapper.OrderMapper;
import com.seckill.order.model.entity.Order;
import com.seckill.order.utils.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class TccOrderParticipant {

    private static final Logger log = LoggerFactory.getLogger(TccOrderParticipant.class);

    private final OrderMapper orderMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public TccOrderParticipant(OrderMapper orderMapper, SnowflakeIdGenerator snowflakeIdGenerator) {
        this.orderMapper = orderMapper;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    public Order tryCreateOrder(Long userId, Long productId, int productType,
                                String productName, int quantity, BigDecimal unitPrice,
                                int timeoutMinutes) {
        // 幂等检查
        int count = orderMapper.countSeckillOrder(userId, productId);
        if (count > 0) {
            log.warn("重复下单：userId={} productId={} 已存在有效订单", userId, productId);
            return null;
        }

        Order order = new Order();
        order.setOrderNo(String.valueOf(snowflakeIdGenerator.nextId()));
        order.setUserId(userId);
        order.setProductId(productId);
        order.setProductType(productType);
        order.setProductName(productName);
        order.setQuantity(quantity);
        order.setUnitPrice(unitPrice);
        order.setAmount(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        order.setStatus(Order.STATUS_TRYING);
        order.setTimeoutAt(LocalDateTime.now().plusMinutes(timeoutMinutes));

        orderMapper.insert(order);
        log.info("TCC Try 创建临时订单 orderNo={} userId={} productId={}", order.getOrderNo(), userId, productId);
        return order;
    }

    public boolean confirm(String orderNo) {
        Order order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            log.error("Confirm订单失败：订单不存在 orderNo={}", orderNo);
            return false;
        }
        if (order.getStatus() == Order.STATUS_PENDING) {
            log.info("订单已确认（幂等）orderNo={}", orderNo);
            return true;
        }
        int updated = orderMapper.confirmOrderStatus(order.getId(), Order.STATUS_TRYING, Order.STATUS_PENDING);
        if (updated == 0) {
            log.error("Confirm订单失败（乐观锁冲突）orderNo={} status={}", orderNo, order.getStatus());
            return false;
        }
        log.info("Confirm订单成功 orderNo={}", orderNo);
        return true;
    }

    public boolean cancel(String orderNo) {
        Order order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            log.error("Cancel订单失败：订单不存在 orderNo={}", orderNo);
            return false;
        }
        if (order.getStatus() == Order.STATUS_CANCELLED) {
            log.info("订单已取消（幂等）orderNo={}", orderNo);
            return true;
        }
        int updated = orderMapper.confirmOrderStatus(order.getId(), Order.STATUS_TRYING, Order.STATUS_CANCELLED);
        if (updated == 0) {
            log.error("Cancel订单失败（乐观锁冲突）orderNo={} status={}", orderNo, order.getStatus());
            return false;
        }
        log.info("Cancel订单成功 orderNo={}", orderNo);
        return true;
    }
}
