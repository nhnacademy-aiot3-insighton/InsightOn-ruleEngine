package com.nhnacademy.insightonruleengine.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rule-engine.group-deletion")
public record GroupDeletionProperties(
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
        requireText(exchange, "GROUP_DELETED Exchange");
        requireText(routingKey, "GROUP_DELETED routing key");
        requireText(queue, "GROUP_DELETED Queue");
        requireText(deadLetterExchange, "GROUP_DELETED DLX");
        requireText(deadLetterRoutingKey, "GROUP_DELETED DLQ routing key");
        requireText(deadLetterQueue, "GROUP_DELETED DLQ");
        if (maxAttempts < 1) {
            throw new IllegalStateException("GROUP_DELETED maxAttempts는 1 이상이어야 합니다.");
        }
        if (initialInterval == null || initialInterval.isNegative() || initialInterval.isZero()) {
            throw new IllegalStateException("GROUP_DELETED initialInterval은 양수여야 합니다.");
        }
        if (multiplier < 1.0) {
            throw new IllegalStateException("GROUP_DELETED retry multiplier는 1 이상이어야 합니다.");
        }
        if (maxInterval == null || maxInterval.compareTo(initialInterval) < 0) {
            throw new IllegalStateException("GROUP_DELETED maxInterval은 initialInterval 이상이어야 합니다.");
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 필수입니다.");
        }
    }
}
