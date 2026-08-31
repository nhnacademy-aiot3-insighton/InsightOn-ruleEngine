package com.nhnacademy.insightonruleengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

//RabbitMQ properties 설정
@ConfigurationProperties(prefix = "rule-engine.action-publisher")
public record ActionPublisherProperties(
        @DefaultValue("insighton.rule-engine-events") String exchange,
        @DefaultValue("ai.alert.action") String alertRoutingKey
) {
}
