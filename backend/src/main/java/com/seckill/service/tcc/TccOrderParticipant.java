package com.seckill.service.tcc;

import com.seckill.mapper.OrderMapper;
import com.seckill.model.entity.Order;
import com.seckill.utils.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * TCC 订单参与者
 * <ul>
 *   <li>Try:     创建临时订单 status=TRYING(-1), 设置 timeout_at</li>
 *   <li>Confirm: TRYING(-1) -> PENDING(0)</li>
 *   <li>Cancel:  TRYING(-1) -> CANCELLED(2)</li>
 * </ul>
 */
@Component
public class TccOrderParticipant {

    private static final Logger log = LoggerFactory.getLogger(TccOrderParticipant.class);

    private final OrderMapper orderMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public TccOrderParticipant(OrderMapper orderMapper, SnowflakeIdGenerator snowflakeIdGenerator) {
        this.orderMapper = orderMapper;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /**
     * Try: 创建临时订单（status=TRYING）
     *
     * @return 创建的订单，null 表示创建失败（如重复下单）
     */
    public Order tryCreateOrder(Long userId, Long productId, int productType,
                                String productName, int quantity,
                                BigDecimal unitPrice, int timeoutMinutes) {
        // 幂等检查
        int count = orderMapper.countSeckillOrder(userId, productId);
        if (count > 0) {
            log.warn("Try创建订单失败：已存在有效订单 userId={} productId={}", userId, productId);
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

        log.info("Try创建订单成功 orderNo={} userId={} productId={} timeoutAt={}",
                order.getOrderNo(), userId, productId, order.getTimeoutAt());
        return order;
    }

    /**
     * Confirm: TRYING -> PENDING（幂等）
     *
     * @return true 表示确认成功
     */
    public boolean confirm(String orderNo) {
        Order order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            log.error("Confirm订单失败：订单不存在 orderNo={}", orderNo);
            return false;
        }
        if (order.getStatus() == Order.STATUS_PENDING) {
            return true; // 已经confirmed，幂等返回成功
        }
        if (order.getStatus() != Order.STATUS_TRYING) {
            log.warn("Confirm订单跳过：状态不为TRYING orderNo={} status={}", orderNo, order.getStatus());
            return false;
        }
        int updated = orderMapper.confirmOrderStatus(order.getId(), Order.STATUS_TRYING, Order.STATUS_PENDING);
        if (updated == 0) {
            log.error("Confirm订单失败（并发冲突）orderNo={}", orderNo);
            return false;
        }
        log.info("Confirm订单成功 orderNo={}", orderNo);
        return true;
    }

    /**
     * Cancel: TRYING -> CANCELLED（幂等）
     *
     * @return true 表示取消成功
     */
    public boolean cancel(String orderNo) {
        Order order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            log.error("Cancel订单失败：订单不存在 orderNo={}", orderNo);
            return false;
        }
        if (order.getStatus() == Order.STATUS_CANCELLED) {
            return true; // 已经cancelled，幂等返回成功
        }
        if (order.getStatus() != Order.STATUS_TRYING) {
            log.warn("Cancel订单跳过：状态不为TRYING orderNo={} status={}", orderNo, order.getStatus());
            return false;
        }
        int updated = orderMapper.confirmOrderStatus(order.getId(), Order.STATUS_TRYING, Order.STATUS_CANCELLED);
        if (updated == 0) {
            log.error("Cancel订单失败（并发冲突）orderNo={}", orderNo);
            return false;
        }
        log.info("Cancel订单成功 orderNo={}", orderNo);
        return true;
    }
}
