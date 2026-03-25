package com.seckill.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    /** 秒杀订单 Topic：3 分区 1 副本 */
    @Bean
    public NewTopic seckillOrderTopic() {
        return new NewTopic("seckill-order", 3, (short) 1);
    }
}
