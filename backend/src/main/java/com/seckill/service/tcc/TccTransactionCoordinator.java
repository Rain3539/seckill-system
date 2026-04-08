package com.seckill.service.tcc;

import com.seckill.model.entity.Order;
import com.seckill.utils.RedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * TCC 事务协调器
 *
 * <p>协调库存参与者和订单参与者完成 Try-Confirm-Cancel 三阶段提交。
 * <ul>
 *   <li>Try 阶段在 Kafka 消费者中执行（同一个本地事务）</li>
 *   <li>Confirm 阶段由支付操作触发</li>
 *   <li>Cancel 阶段由取消订单或超时定时任务触发</li>
 * </ul>
 */
@Component
public class TccTransactionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(TccTransactionCoordinator.class);

    /** TCC 事务默认超时时间（分钟），超过此时间未 confirm 则自动 cancel */
    public static final int DEFAULT_TIMEOUT_MINUTES = 15;

    private final TccStockParticipant stockParticipant;
    private final TccOrderParticipant orderParticipant;
    private final RedisUtils redisUtils;

    public TccTransactionCoordinator(TccStockParticipant stockParticipant,
                                     TccOrderParticipant orderParticipant,
                                     RedisUtils redisUtils) {
        this.stockParticipant = stockParticipant;
        this.orderParticipant = orderParticipant;
        this.redisUtils = redisUtils;
    }

    public TccStockParticipant getStockParticipant() {
        return stockParticipant;
    }

    /**
     * TCC Try 阶段
     *
     * <p>在 Kafka 消费者中调用，在同一个本地事务内完成：
     * <ol>
     *   <li>库存 Try：预留库存 (avail_stock -> locked_stock)</li>
     *   <li>订单 Try：创建 TRYING 临时订单</li>
     * </ol>
     *
     * @return 创建的订单，null 表示 Try 失败
     */
    @Transactional(rollbackFor = Exception.class)
    public Order tryPhase(Long userId, Long productId, int quantity,
                          String productName, BigDecimal unitPrice) {
        log.info("TCC Try开始 userId={} productId={} quantity={}", userId, productId, quantity);

        // Step 1: 库存 Try（预留）
        boolean stockReserved = stockParticipant.tryReserve(productId, quantity);
        if (!stockReserved) {
            log.error("TCC Try失败：库存预留失败 productId={}", productId);
            return null;
        }

        // Step 2: 订单 Try（创建临时订单）
        Order order = orderParticipant.tryCreateOrder(
                userId, productId, 1, productName, quantity, unitPrice,
                DEFAULT_TIMEOUT_MINUTES);
        if (order == null) {
            // 订单创建失败（如重复下单），需释放已预留的库存
            log.error("TCC Try失败：订单创建失败，回滚库存 userId={} productId={}", userId, productId);
            stockParticipant.cancel(productId, quantity);
            return null;
        }

        log.info("TCC Try成功 orderNo={} userId={} productId={}", order.getOrderNo(), userId, productId);
        return order;
    }

    /**
     * TCC Confirm 阶段（支付成功后触发）
     *
     * <ol>
     *   <li>订单 Confirm：TRYING(-1) -> PENDING(0)</li>
     *   <li>库存 Confirm：locked_stock 永久扣减</li>
     * </ol>
     *
     * @return true 表示确认成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmPhase(String orderNo, Long productId, int quantity) {
        log.info("TCC Confirm开始 orderNo={} productId={} quantity={}", orderNo, productId, quantity);

        // Step 1: 订单 Confirm
        boolean orderConfirmed = orderParticipant.confirm(orderNo);
        if (!orderConfirmed) {
            log.error("TCC Confirm失败：订单确认失败 orderNo={}", orderNo);
            return false;
        }

        // Step 2: 库存 Confirm
        boolean stockConfirmed = stockParticipant.confirm(productId, quantity);
        if (!stockConfirmed) {
            log.error("TCC Confirm失败：库存确认失败 productId={}", productId);
            return false;
        }

        log.info("TCC Confirm成功 orderNo={}", orderNo);
        return true;
    }

    /**
     * TCC Cancel 阶段（取消订单 / 超时触发）
     *
     * <ol>
     *   <li>订单 Cancel：TRYING(-1) -> CANCELLED(2)</li>
     *   <li>库存 Cancel：locked_stock -> avail_stock</li>
     *   <li>Redis 库存回滚</li>
     * </ol>
     *
     * @return true 表示取消成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelPhase(String orderNo, Long productId, int quantity) {
        log.info("TCC Cancel开始 orderNo={} productId={} quantity={}", orderNo, productId, quantity);

        // Step 1: 订单 Cancel
        boolean orderCancelled = orderParticipant.cancel(orderNo);
        if (!orderCancelled) {
            log.error("TCC Cancel失败：订单取消失败 orderNo={}", orderNo);
            return false;
        }

        // Step 2: 库存 Cancel
        boolean stockCancelled = stockParticipant.cancel(productId, quantity);
        if (!stockCancelled) {
            log.error("TCC Cancel失败：库存释放失败 productId={}", productId);
            return false;
        }

        // Step 3: Redis 库存回滚
        String stockKey = "seckill:stock:" + productId;
        redisUtils.increment(stockKey, quantity);
        log.info("TCC Cancel成功 orderNo={} Redis库存已回滚", orderNo);

        return true;
    }
}
