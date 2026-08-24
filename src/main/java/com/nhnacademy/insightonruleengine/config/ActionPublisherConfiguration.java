package com.nhnacademy.insightonruleengine.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ActionPublisherProperties.class)
public class ActionPublisherConfiguration {

    @Bean
    TopicExchange actionEventExchange(ActionPublisherProperties properties) {
        return new TopicExchange(properties.exchange(),true,false);
    }
}
