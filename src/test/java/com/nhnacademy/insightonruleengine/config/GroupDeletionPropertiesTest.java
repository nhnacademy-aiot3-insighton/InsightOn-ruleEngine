package com.nhnacademy.insightonruleengine.common.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupDeletionPropertiesTest {

    @Test
    @DisplayName("활성 설정은 공유 RabbitMQ 계약과 재시도 값을 모두 검증합니다")
    void enabledConfigurationTest() {
        GroupDeletionProperties properties = properties("insighton.core-events", 3);

        assertDoesNotThrow(properties::validateEnabledConfiguration);
    }

    @Test
    @DisplayName("활성 상태에서 Exchange가 없거나 재시도 횟수가 잘못되면 거부합니다")
    void invalidEnabledConfigurationTest() {
        GroupDeletionProperties missingExchange = properties(" ", 3);
        GroupDeletionProperties invalidAttempts = properties("insighton.core-events", 0);

        assertThrows(
                IllegalArgumentException.class,
                missingExchange::validateEnabledConfiguration
        );
        assertThrows(
                IllegalStateException.class,
                invalidAttempts::validateEnabledConfiguration
        );
    }

    @Test
    @DisplayName("활성 상태에서는 모든 RabbitMQ 이름이 필요합니다")
    void missingRabbitNamesTest() {
        assertInvalidNames(properties(null, "group.deleted", "queue", "dlx", "dlq", "dlq"));
        assertInvalidNames(properties("exchange", null, "queue", "dlx", "dlq", "dlq"));
        assertInvalidNames(properties("exchange", "group.deleted", null, "dlx", "dlq", "dlq"));
        assertInvalidNames(properties("exchange", "group.deleted", "queue", null, "dlq", "dlq"));
        assertInvalidNames(properties("exchange", "group.deleted", "queue", "dlx", null, "dlq"));
        assertInvalidNames(properties("exchange", "group.deleted", "queue", "dlx", "dlq", null));
    }

    @Test
    @DisplayName("활성 상태에서는 올바른 재시도 간격과 배수가 필요합니다")
    void invalidRetrySettingsTest() {
        assertInvalidRetry(properties(null, 2.0, Duration.ofSeconds(10)));
        assertInvalidRetry(properties(Duration.ofSeconds(-1), 2.0, Duration.ofSeconds(10)));
        assertInvalidRetry(properties(Duration.ofSeconds(1), 0.5, Duration.ofSeconds(10)));
        assertInvalidRetry(properties(Duration.ofSeconds(1), 2.0, null));
        assertInvalidRetry(properties(Duration.ofSeconds(2), 2.0, Duration.ofSeconds(1)));
    }

    @Test
    @DisplayName("비활성 설정은 RabbitMQ 값을 요구하지 않습니다")
    void disabledConfigurationTest() {
        GroupDeletionProperties properties = new GroupDeletionProperties(
                false, null, null, null, null, null, null, 0, null, 0, null
        );

        assertDoesNotThrow(properties::validateEnabledConfiguration);
    }

    private GroupDeletionProperties properties(String exchange, int maxAttempts) {
        return new GroupDeletionProperties(
                true,
                exchange,
                "group.deleted",
                "rule-engine.group-deleted",
                "rule-engine.group-deleted.dlx",
                "rule-engine.group-deleted.dlq",
                "rule-engine.group-deleted.dlq",
                maxAttempts,
                Duration.ofSeconds(1),
                2.0,
                Duration.ofSeconds(5)
        );
    }

    private GroupDeletionProperties properties(
            String exchange,
            String routingKey,
            String queue,
            String deadLetterExchange,
            String deadLetterRoutingKey,
            String deadLetterQueue
    ) {
        return new GroupDeletionProperties(
                true,
                exchange,
                routingKey,
                queue,
                deadLetterExchange,
                deadLetterRoutingKey,
                deadLetterQueue,
                3,
                Duration.ofSeconds(1),
                2.0,
                Duration.ofSeconds(10)
        );
    }

    private GroupDeletionProperties properties(
            Duration initialInterval,
            double multiplier,
            Duration maxInterval
    ) {
        return new GroupDeletionProperties(
                true,
                "exchange",
                "group.deleted",
                "queue",
                "dlx",
                "dlq",
                "dlq",
                3,
                initialInterval,
                multiplier,
                maxInterval
        );
    }

    private void assertInvalidNames(GroupDeletionProperties properties) {
        assertThrows(IllegalArgumentException.class, properties::validateEnabledConfiguration);
    }

    private void assertInvalidRetry(GroupDeletionProperties properties) {
        assertThrows(IllegalStateException.class, properties::validateEnabledConfiguration);
    }
}
