package com.seckill.order.controller;

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
    public ResultVO<Order> place(@Valid @RequestBody PlaceOrderDTO dto, HttpServletRequest req) {
        return ResultVO.success(orderService.placeOrder(extractUserId(req), dto));
    }

    @PostMapping("/pay/{orderNo}")
    public ResultVO<Order> pay(@PathVariable String orderNo, HttpServletRequest req) {
        return ResultVO.success(orderService.payOrder(extractUserId(req), orderNo));
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
