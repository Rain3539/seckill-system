package com.seckill.stock.service;

import com.seckill.stock.model.dto.SeckillOrderMessage;

public interface KafkaProducerService {
    void sendSeckillOrder(SeckillOrderMessage message);
}
