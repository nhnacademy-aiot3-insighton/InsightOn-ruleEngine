package com.nhnacademy.insightonruleengine.common.config;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// fixme 해당하는 queue core에게 check하기
@Configuration
@ConditionalOnProperty(name = "", havingValue = "true")
public class RabbitMqConfig {

    @Bean
    public Queue sensorEventQueue(@Value("${}") String queueName) {
        return new Queue(queueName, true);
    }
}
