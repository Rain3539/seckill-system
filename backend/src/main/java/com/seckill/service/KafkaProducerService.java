package com.seckill.service;

import com.seckill.model.dto.SeckillOrderMessage;

public interface KafkaProducerService {
    void sendSeckillOrder(SeckillOrderMessage message);
}
