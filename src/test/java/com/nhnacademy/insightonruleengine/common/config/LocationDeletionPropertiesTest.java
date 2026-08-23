package com.nhnacademy.insightonruleengine.common.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocationDeletionPropertiesTest {

    @Test
    @DisplayName("기능이 꺼져 있으면 아직 제공되지 않은 인프라 설정을 요구하지 않습니다")
    void disabledConfigurationTest() {
        LocationDeletionProperties properties = new LocationDeletionProperties(
                false, null, null, null, null, null, null, 0, null, 0, null
        );

        assertDoesNotThrow(properties::validateEnabledConfiguration);
    }

    @Test
    @DisplayName("기능이 켜져 있으면 Queue, DLQ, bounded retry 설정이 모두 필요합니다")
    void enabledConfigurationValidationTest() {
        LocationDeletionProperties valid = properties(3, Duration.ofMillis(100), 2, Duration.ofSeconds(1));
        LocationDeletionProperties invalidAttempts = properties(0, Duration.ofMillis(100), 2, Duration.ofSeconds(1));
        LocationDeletionProperties invalidBackoff = properties(3, Duration.ZERO, 2, Duration.ofSeconds(1));

        assertDoesNotThrow(valid::validateEnabledConfiguration);
        assertThrows(IllegalStateException.class, invalidAttempts::validateEnabledConfiguration);
        assertThrows(IllegalStateException.class, invalidBackoff::validateEnabledConfiguration);
    }

    private LocationDeletionProperties properties(
            int maxAttempts,
            Duration initialInterval,
            double multiplier,
            Duration maxInterval
    ) {
        return new LocationDeletionProperties(
                true,
                "insighton.core-events",
                "location.deleted",
                "rule-engine.location-deleted",
                "rule-engine.location-deleted.dlx",
                "rule-engine.location-deleted.dlq",
                "rule-engine.location-deleted.dlq",
                maxAttempts,
                initialInterval,
                multiplier,
                maxInterval
        );
    }
}
