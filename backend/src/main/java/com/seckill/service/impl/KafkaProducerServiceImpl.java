package com.seckill.service.impl;

import com.seckill.model.dto.SeckillOrderMessage;
import com.seckill.service.KafkaProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class KafkaProducerServiceImpl implements KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerServiceImpl.class);
    private static final String TOPIC = "seckill-order";

    private final KafkaTemplate<String, SeckillOrderMessage> kafkaTemplate;

    public KafkaProducerServiceImpl(KafkaTemplate<String, SeckillOrderMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void sendSeckillOrder(SeckillOrderMessage message) {
        String key = message.getUserId() + ":" + message.getSeckillProductId();
        CompletableFuture<SendResult<String, SeckillOrderMessage>> future =
                kafkaTemplate.send(TOPIC, key, message);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Kafka 消息发送成功 topic={} partition={} offset={} messageId={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        message.getMessageId());
            } else {
                log.error("Kafka 消息发送失败 messageId={}, error={}", message.getMessageId(), ex.getMessage(), ex);
            }
        });
    }
}
