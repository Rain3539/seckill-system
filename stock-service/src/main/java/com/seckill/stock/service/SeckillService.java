package com.seckill.stock.service;

import com.seckill.stock.model.dto.SeckillRequestDTO;
import com.seckill.stock.model.entity.Order;
import java.util.Map;

public interface SeckillService {
    Map<String, Object> doSeckill(Long userId, SeckillRequestDTO dto);
    Order getSeckillOrder(Long userId, Long seckillProductId);
}
