package com.seckill.order.service;

import com.seckill.order.model.dto.PlaceOrderDTO;
import com.seckill.order.model.entity.Order;
import java.util.List;

public interface OrderService {
    Order placeOrder(Long userId, PlaceOrderDTO dto);
    Order payOrder(Long userId, String orderNo);
    Order cancelOrder(Long userId, String orderNo);
    List<Order> getMyOrders(Long userId);
    Order getByOrderNo(String orderNo);
}
