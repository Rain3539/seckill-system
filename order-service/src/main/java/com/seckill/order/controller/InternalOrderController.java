package com.seckill.order.controller;

import com.seckill.order.mapper.OrderMapper;
import com.seckill.order.model.entity.Order;
import com.seckill.order.model.vo.ResultVO;
import com.seckill.order.tcc.TccOrderParticipant;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/order/internal")
public class InternalOrderController {

    private static final int DEFAULT_TIMEOUT_MINUTES = 15;

    private final TccOrderParticipant orderParticipant;
    private final OrderMapper orderMapper;

    public InternalOrderController(TccOrderParticipant orderParticipant, OrderMapper orderMapper) {
        this.orderParticipant = orderParticipant;
        this.orderMapper = orderMapper;
    }

    @PostMapping("/create")
    public ResultVO<Order> createTryingOrder(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        Long productId = Long.valueOf(params.get("productId").toString());
        int quantity = Integer.parseInt(params.get("quantity").toString());
        String productName = params.get("productName").toString();
        BigDecimal unitPrice = new BigDecimal(params.get("unitPrice").toString());

        Order order = orderParticipant.tryCreateOrder(
                userId, productId, 1, productName, quantity, unitPrice,
                DEFAULT_TIMEOUT_MINUTES);

        if (order == null) {
            return ResultVO.fail("订单创建失败（可能重复下单）");
        }
        return ResultVO.success(order);
    }

    @PostMapping("/confirm")
    public ResultVO<Boolean> confirmOrder(@RequestParam String orderNo) {
        boolean ok = orderParticipant.confirm(orderNo);
        return ok ? ResultVO.success(true) : ResultVO.fail("订单确认失败");
    }

    @PostMapping("/cancel")
    public ResultVO<Boolean> cancelOrder(@RequestParam String orderNo) {
        boolean ok = orderParticipant.cancel(orderNo);
        return ok ? ResultVO.success(true) : ResultVO.fail("订单取消失败");
    }

    @GetMapping("/count")
    public ResultVO<Integer> countSeckillOrder(@RequestParam Long userId, @RequestParam Long productId) {
        int count = orderMapper.countSeckillOrder(userId, productId);
        return ResultVO.success(count);
    }

    @GetMapping("/timeout")
    public ResultVO<List<Order>> timeoutOrders() {
        return ResultVO.success(orderMapper.findTimeoutTryingOrders());
    }

    @GetMapping("/user-product")
    public ResultVO<Order> findByUserProduct(@RequestParam Long userId, @RequestParam Long productId) {
        Order order = orderMapper.findByUserIdAndProductId(userId, productId);
        return ResultVO.success(order);
    }
}
