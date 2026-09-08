package com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rule-engine.core-lifecycle-events")
public record CoreLifecycleEventProperties(
        boolean enabled,
        String exchange,
        RetryProperties retry,
        EventBindingProperties groupDeleted,
        EventBindingProperties locationDeleted
) {

    public void validateEnabledConfiguration() {
        if (!enabled) {
            return;
        }

        requireText(exchange, "Core lifecycle event Exchange");
        if (retry == null) {
            throw new IllegalStateException("Core lifecycle event retry 설정은 필수입니다.");
        }
        if (groupDeleted == null) {
            throw new IllegalArgumentException("GROUP_DELETED RabbitMQ 설정은 필수입니다.");
        }
        if (locationDeleted == null) {
            throw new IllegalArgumentException("LOCATION_DELETED RabbitMQ 설정은 필수입니다.");
        }

        retry.validate();
        groupDeleted.validate("GROUP_DELETED");
        locationDeleted.validate("LOCATION_DELETED");
    }

    public record RetryProperties(
            int maxAttempts,
            Duration initialInterval,
            double multiplier,
            Duration maxInterval
    ) {

        private void validate() {
            if (maxAttempts < 1) {
                throw new IllegalStateException("Core lifecycle event maxAttempts는 1 이상이어야 합니다.");
            }
            if (initialInterval == null || initialInterval.isNegative() || initialInterval.isZero()) {
                throw new IllegalStateException("Core lifecycle event initialInterval은 양수여야 합니다.");
            }
            if (multiplier < 1.0) {
                throw new IllegalStateException("Core lifecycle event retry multiplier는 1 이상이어야 합니다.");
            }
            if (maxInterval == null || maxInterval.compareTo(initialInterval) < 0) {
                throw new IllegalStateException(
                        "Core lifecycle event maxInterval은 initialInterval 이상이어야 합니다."
                );
            }
        }
    }

    public record EventBindingProperties(
            String routingKey,
            String queue,
            String deadLetterExchange,
            String deadLetterRoutingKey,
            String deadLetterQueue
    ) {

        private void validate(String eventName) {
            requireText(routingKey, eventName + " routing key");
            requireText(queue, eventName + " Queue");
            requireText(deadLetterExchange, eventName + " DLX");
            requireText(deadLetterRoutingKey, eventName + " DLQ routing key");
            requireText(deadLetterQueue, eventName + " DLQ");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 필수입니다.");
        }
    }
}
