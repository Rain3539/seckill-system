package com.seckill.order.mapper;

import com.seckill.order.model.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface OrderMapper {
    Order findByOrderNo(@Param("orderNo") String orderNo);
    List<Order> findByUserId(@Param("userId") Long userId);
    Order findByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
    int countSeckillOrder(@Param("userId") Long userId, @Param("productId") Long productId);
    int insert(Order order);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
    int confirmOrderStatus(@Param("id") Long id, @Param("expectedStatus") Integer expectedStatus, @Param("newStatus") Integer newStatus);
    List<Order> findTimeoutTryingOrders();
}
