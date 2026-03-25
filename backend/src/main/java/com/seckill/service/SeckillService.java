package com.seckill.service;

import com.seckill.model.dto.SeckillRequestDTO;
import com.seckill.model.entity.Order;

import java.util.Map;

public interface SeckillService {
    /**
     * 秒杀下单（异步）
     * Redis 预减库存 + Kafka 异步落库，立即返回排队结果
     */
    Map<String, Object> doSeckill(Long userId, SeckillRequestDTO dto);

    /**
     * 查询秒杀订单结果
     */
    Order getSeckillOrder(Long userId, Long seckillProductId);
}
