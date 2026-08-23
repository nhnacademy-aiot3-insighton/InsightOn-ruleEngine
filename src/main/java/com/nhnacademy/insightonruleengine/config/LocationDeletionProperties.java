package com.nhnacademy.insightonruleengine.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rule-engine.location-deletion")
public record LocationDeletionProperties(
        boolean enabled,
        String exchange,
        String routingKey,
        String queue,
        String deadLetterExchange,
        String deadLetterRoutingKey,
        String deadLetterQueue,
        int maxAttempts,
        Duration initialInterval,
        double multiplier,
        Duration maxInterval
) {
    public void validateEnabledConfiguration() {
        if (!enabled) {
            return;
        }
        requireText(exchange, "LOCATION_DELETED Exchange");
        requireText(routingKey, "LOCATION_DELETED routing key");
        requireText(queue, "LOCATION_DELETED Queue");
        requireText(deadLetterExchange, "LOCATION_DELETED DLX");
        requireText(deadLetterRoutingKey, "LOCATION_DELETED DLQ routing key");
        requireText(deadLetterQueue, "LOCATION_DELETED DLQ");
        if (maxAttempts < 1) {
            throw new IllegalStateException("LOCATION_DELETED maxAttempts는 1 이상이어야 합니다.");
        }
        if (initialInterval == null || initialInterval.isNegative() || initialInterval.isZero()) {
            throw new IllegalStateException("LOCATION_DELETED initialInterval은 양수여야 합니다.");
        }
        if (multiplier < 1.0) {
            throw new IllegalStateException("LOCATION_DELETED retry multiplier는 1 이상이어야 합니다.");
        }
        if (maxInterval == null || maxInterval.compareTo(initialInterval) < 0) {
            throw new IllegalStateException("LOCATION_DELETED maxInterval은 initialInterval 이상이어야 합니다.");
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 필수입니다.");
        }
    }
}
