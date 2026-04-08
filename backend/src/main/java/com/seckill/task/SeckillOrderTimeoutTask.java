package com.seckill.task;

import com.seckill.datasource.DS;
import com.seckill.datasource.DataSourceType;
import com.seckill.mapper.OrderMapper;
import com.seckill.model.entity.Order;
import com.seckill.service.tcc.TccTransactionCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 秒杀订单超时自动取消定时任务
 *
 * <p>每 30 秒扫描一次 status=TRYING 且 timeout_at &lt; NOW() 的订单，
 * 对每个超时订单执行 TCC Cancel 释放库存。
 */
@Component
public class SeckillOrderTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderTimeoutTask.class);

    private final OrderMapper orderMapper;
    private final TccTransactionCoordinator tccCoordinator;

    public SeckillOrderTimeoutTask(OrderMapper orderMapper,
                                   TccTransactionCoordinator tccCoordinator) {
        this.orderMapper = orderMapper;
        this.tccCoordinator = tccCoordinator;
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 60000)
    @DS(DataSourceType.MASTER)
    public void cancelTimeoutOrders() {
        List<Order> timeoutOrders = orderMapper.findTimeoutTryingOrders();
        if (timeoutOrders.isEmpty()) return;

        log.info("发现{}个超时TRYING订单，开始自动取消", timeoutOrders.size());

        for (Order order : timeoutOrders) {
            try {
                boolean cancelled = tccCoordinator.cancelPhase(
                        order.getOrderNo(),
                        order.getProductId(),
                        order.getQuantity());
                if (cancelled) {
                    log.info("超时订单自动取消成功 orderNo={}", order.getOrderNo());
                } else {
                    log.error("超时订单自动取消失败 orderNo={}", order.getOrderNo());
                }
            } catch (Exception e) {
                log.error("超时订单自动取消异常 orderNo={} error={}",
                        order.getOrderNo(), e.getMessage(), e);
            }
        }
    }
}
