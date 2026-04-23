package com.seckill.order.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.seckill.order.model.dto.PlaceOrderDTO;
import com.seckill.order.model.entity.Order;
import com.seckill.order.model.vo.ResultVO;
import com.seckill.order.service.OrderService;
import com.seckill.order.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;
    private final JwtUtils jwtUtils;

    public OrderController(OrderService orderService, JwtUtils jwtUtils) {
        this.orderService = orderService;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/place")
    @SentinelResource(value = "order-place",
            blockHandler = "placeBlockHandler",
            fallback = "placeFallback")
    public ResultVO<Order> place(@Valid @RequestBody PlaceOrderDTO dto, HttpServletRequest req) {
        return ResultVO.success(orderService.placeOrder(extractUserId(req), dto));
    }

    public ResultVO<Order> placeBlockHandler(PlaceOrderDTO dto, HttpServletRequest req, BlockException ex) {
        return ResultVO.fail(429, "下单请求过于频繁，请稍后重试");
    }

    public ResultVO<Order> placeFallback(PlaceOrderDTO dto, HttpServletRequest req, Throwable t) {
        return ResultVO.fail(503, "订单服务降级，请稍后再试");
    }

    @PostMapping("/pay/{orderNo}")
    @SentinelResource(value = "order-pay",
            blockHandler = "payBlockHandler",
            fallback = "payFallback")
    public ResultVO<Order> pay(@PathVariable String orderNo, HttpServletRequest req) {
        return ResultVO.success(orderService.payOrder(extractUserId(req), orderNo));
    }

    public ResultVO<Order> payBlockHandler(String orderNo, HttpServletRequest req, BlockException ex) {
        return ResultVO.fail(429, "支付请求过于频繁，请稍后重试");
    }

    public ResultVO<Order> payFallback(String orderNo, HttpServletRequest req, Throwable t) {
        return ResultVO.fail(503, "支付服务降级，请稍后再试");
    }

    @PostMapping("/cancel/{orderNo}")
    public ResultVO<Order> cancel(@PathVariable String orderNo, HttpServletRequest req) {
        return ResultVO.success(orderService.cancelOrder(extractUserId(req), orderNo));
    }

    @GetMapping("/my")
    public ResultVO<List<Order>> myOrders(HttpServletRequest req) {
        return ResultVO.success(orderService.getMyOrders(extractUserId(req)));
    }

    @GetMapping("/{orderNo}")
    public ResultVO<Order> detail(@PathVariable String orderNo) {
        return ResultVO.success(orderService.getByOrderNo(orderNo));
    }

    private Long extractUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            if (jwtUtils.validateToken(token)) return jwtUtils.getUserIdFromToken(token);
        }
        throw new RuntimeException("未登录或Token已过期，请重新登录");
    }
}
