package com.nhnacademy.insightonruleengine.common.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "rule-engine.telemetry-routing", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TelemetryRoutingProperties.class)
public class TelemetryRoutingConfiguration {

    private static final int QUEUE_COUNT = 16;

    //locationId를 라우팅 키로 hash, Exchange로 선언해줍니다.
    @Bean
    CustomExchange telemetryExchange(TelemetryRoutingProperties properties) {
        properties.validateEnabledConfiguration();
        Map<String, Object> arguments = new HashMap<>();
        if (properties.hashHeader() != null && !properties.hashHeader().isBlank()) {
            arguments.put("hash-header", properties.hashHeader());
        }
        return new CustomExchange(
                properties.exchange(),
                "x-consistent-hash",
                true,
                false,
                arguments
        );
    }

    @Bean
    Declarables telemetryQueueTopology(
            CustomExchange telemetryConsistentHashExchange,
            TelemetryRoutingProperties properties
    ) {
        properties.validateEnabledConfiguration();
        List<Declarable> declarables = new ArrayList<>();
        for (int i = 0; i < QUEUE_COUNT; i++) {
            Queue queue = QueueBuilder.durable(properties.queueName(i)).build();
            Binding binding = BindingBuilder
                    .bind(queue)
                    .to(telemetryConsistentHashExchange)
                    .with(String.valueOf("1"))
                    .noargs();
            declarables.add(queue);
            declarables.add(binding);
        }
        return new Declarables(declarables);
    }

}
