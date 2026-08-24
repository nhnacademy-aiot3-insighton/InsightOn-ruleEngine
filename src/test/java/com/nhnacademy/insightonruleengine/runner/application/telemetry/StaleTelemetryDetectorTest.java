package com.nhnacademy.insightonruleengine.runner.application.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StaleTelemetryDetectorTest {

    private final StaleTelemetryDetector detector = new StaleTelemetryDetector();

    @Test
    @DisplayName("같은 Sensor의 최신 시각보다 과거이거나 같은 Telemetry는 stale입니다")
    void staleWithinSameSensorTest() {
        Instant latest = Instant.parse("2026-08-20T01:05:00Z");

        assertThat(detector.isStale(1L, 10L, 101L, latest)).isFalse();
        assertThat(detector.isStale(1L, 10L, 101L, latest)).isTrue();
        assertThat(detector.isStale(1L, 10L, 101L, Instant.parse("2026-08-20T01:03:00Z")))
                .isTrue();
    }

    @Test
    @DisplayName("다른 Sensor의 최신 시각은 서로 간섭하지 않습니다")
    void watermarkIsSeparatedBySensorTest() {
        detector.isStale(1L, 10L, 101L, Instant.parse("2026-08-20T01:05:00Z"));

        assertThat(detector.isStale(1L, 10L, 102L, Instant.parse("2026-08-20T01:03:00Z")))
                .isFalse();
    }
}
