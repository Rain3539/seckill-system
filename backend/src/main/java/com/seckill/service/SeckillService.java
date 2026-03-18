package com.seckill.service;

import com.seckill.model.dto.SeckillRequestDTO;
import com.seckill.model.entity.Order;

public interface SeckillService {
    Order doSeckill(Long userId, SeckillRequestDTO dto);
}
