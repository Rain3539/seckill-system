package com.seckill.mapper;

import com.seckill.model.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface OrderMapper {
    Order findByOrderNo(@Param("orderNo") String orderNo);
    List<Order> findByUserId(@Param("userId") Long userId);

    /** 按用户 ID + 商品 ID 查询秒杀订单（用于秒杀订单查询） */
    Order findByUserIdAndProductId(@Param("userId") Long userId,
                                   @Param("productId") Long productId);

    /** 检查用户是否已对该秒杀商品下过单（防重复，排除TRYING和CANCELLED） */
    int countSeckillOrder(@Param("userId") Long userId,
                          @Param("productId") Long productId);

    int insert(Order order);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** TCC状态转换：带条件的状态更新（乐观），用于Confirm和Cancel */
    int confirmOrderStatus(@Param("id") Long id,
                           @Param("expectedStatus") Integer expectedStatus,
                           @Param("newStatus") Integer newStatus);

    /** 查询超时的TRYING订单（定时任务用） */
    List<Order> findTimeoutTryingOrders();
}
