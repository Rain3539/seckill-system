package com.seckill.stock.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic seckillOrderTopic() {
        return new NewTopic("seckill-order", 3, (short) 1);
    }
}
