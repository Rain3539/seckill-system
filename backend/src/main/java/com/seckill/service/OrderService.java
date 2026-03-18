package com.seckill.service;

import com.seckill.model.dto.PlaceOrderDTO;
import com.seckill.model.entity.Order;
import java.util.List;

public interface OrderService {
    /** 普通商品下单 */
    Order placeOrder(Long userId, PlaceOrderDTO dto);
    /** 支付订单（模拟） */
    Order payOrder(Long userId, String orderNo);
    /** 取消订单 */
    Order cancelOrder(Long userId, String orderNo);
    List<Order> getMyOrders(Long userId);
    Order getByOrderNo(String orderNo);
}
