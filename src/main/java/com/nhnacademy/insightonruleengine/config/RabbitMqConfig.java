package com.nhnacademy.insightonruleengine.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "rule-engine.rabbitmq.enabled", havingValue = "true")
public class RabbitMqConfig {

    @Bean
    public Queue sensorEventQueue(@Value("${rule-engine.sensor-event.queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public TopicExchange sensorEventExchange(@Value("${rule-engine.sensor-event.exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Binding sensorEventBinding(
            Queue sensorEventQueue,
            TopicExchange sensorEventExchange,
            @Value("${rule-engine.sensor-event.routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(sensorEventQueue)
                .to(sensorEventExchange)
                .with(routingKey);
    }
}
