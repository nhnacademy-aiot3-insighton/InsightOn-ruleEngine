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
        GroupDeletionProperties properties = properties(true, "insighton.core-events", 3);

        assertDoesNotThrow(properties::validateEnabledConfiguration);
    }

    @Test
    @DisplayName("활성 상태에서 Exchange가 없거나 재시도 횟수가 잘못되면 거부합니다")
    void invalidEnabledConfigurationTest() {
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(true, " ", 3).validateEnabledConfiguration()
        );
        assertThrows(
                IllegalStateException.class,
                () -> properties(true, "insighton.core-events", 0).validateEnabledConfiguration()
        );
    }

    @Test
    @DisplayName("비활성 설정은 RabbitMQ 값을 요구하지 않습니다")
    void disabledConfigurationTest() {
        GroupDeletionProperties properties = new GroupDeletionProperties(
                false, null, null, null, null, null, null, 0, null, 0, null
        );

        assertDoesNotThrow(properties::validateEnabledConfiguration);
    }

    private GroupDeletionProperties properties(boolean enabled, String exchange, int maxAttempts) {
        return new GroupDeletionProperties(
                enabled,
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
}
