package com.seckill.controller;

import com.seckill.model.dto.PlaceOrderDTO;
import com.seckill.model.entity.Order;
import com.seckill.model.vo.ResultVO;
import com.seckill.service.OrderService;
import com.seckill.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;
    private final JwtUtils     jwtUtils;

    public OrderController(OrderService orderService, JwtUtils jwtUtils) {
        this.orderService = orderService;
        this.jwtUtils     = jwtUtils;
    }

    /** 普通商品下单  POST /api/order/place */
    @PostMapping("/place")
    public ResultVO<Order> place(@Valid @RequestBody PlaceOrderDTO dto,
                                 HttpServletRequest req) {
        return ResultVO.success(orderService.placeOrder(extractUserId(req), dto));
    }

    /** 支付订单  POST /api/order/pay/{orderNo} */
    @PostMapping("/pay/{orderNo}")
    public ResultVO<Order> pay(@PathVariable String orderNo, HttpServletRequest req) {
        return ResultVO.success(orderService.payOrder(extractUserId(req), orderNo));
    }

    /** 取消订单  POST /api/order/cancel/{orderNo} */
    @PostMapping("/cancel/{orderNo}")
    public ResultVO<Order> cancel(@PathVariable String orderNo, HttpServletRequest req) {
        return ResultVO.success(orderService.cancelOrder(extractUserId(req), orderNo));
    }

    /** 我的订单  GET /api/order/my */
    @GetMapping("/my")
    public ResultVO<List<Order>> myOrders(HttpServletRequest req) {
        return ResultVO.success(orderService.getMyOrders(extractUserId(req)));
    }

    /** 订单详情  GET /api/order/{orderNo} */
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
