package com.seckill.order.task;

import com.seckill.order.datasource.DS;
import com.seckill.order.datasource.DataSourceType;
import com.seckill.order.mapper.OrderMapper;
import com.seckill.order.model.entity.Order;
import com.seckill.order.model.vo.ResultVO;
import com.seckill.order.tcc.TccOrderParticipant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
public class SeckillOrderTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderTimeoutTask.class);

    private final OrderMapper orderMapper;
    private final TccOrderParticipant tccOrderParticipant;
    private final RestTemplate restTemplate;

    @Value("${stock-service.url}")
    private String stockServiceUrl;

    public SeckillOrderTimeoutTask(OrderMapper orderMapper,
                                   TccOrderParticipant tccOrderParticipant,
                                   RestTemplate restTemplate) {
        this.orderMapper = orderMapper;
        this.tccOrderParticipant = tccOrderParticipant;
        this.restTemplate = restTemplate;
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 60000)
    @DS(DataSourceType.MASTER)
    public void cancelTimeoutOrders() {
        List<Order> timeoutOrders = orderMapper.findTimeoutTryingOrders();
        if (timeoutOrders.isEmpty()) return;

        log.info("发现{}个超时TRYING订单，开始自动取消", timeoutOrders.size());

        for (Order order : timeoutOrders) {
            try {
                // 1. 本地订单取消
                boolean orderCancelled = tccOrderParticipant.cancel(order.getOrderNo());
                if (!orderCancelled) continue;

                // 2. 远程库存取消
                try {
                    String url = stockServiceUrl + "/api/stock/cancel?productId=" + order.getProductId() + "&quantity=" + order.getQuantity();
                    restTemplate.postForObject(url, null, ResultVO.class);
                } catch (Exception e) {
                    log.error("远程库存取消失败 orderNo={} error={}", order.getOrderNo(), e.getMessage());
                }

                log.info("超时订单自动取消成功 orderNo={}", order.getOrderNo());
            } catch (Exception e) {
                log.error("超时订单自动取消异常 orderNo={} error={}", order.getOrderNo(), e.getMessage(), e);
            }
        }
    }
}
