package com.nhnacademy.insightonruleengine.runner.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StaleTelemetryDetectorTest {

    private final StaleTelemetryDetector detector = new StaleTelemetryDetector();

    @Test
    @DisplayName("같은 Sensor의 최신 시각보다 과거이거나 같은 Telemetry는 stale입니다")
    void staleWithinSameSensorTest() {
        OffsetDateTime latest = OffsetDateTime.parse("2026-08-20T10:05:00+09:00");

        assertThat(detector.isStale(1L, 10L, "sensor-1", latest)).isFalse();
        assertThat(detector.isStale(1L, 10L, "sensor-1", latest)).isTrue();
        assertThat(detector.isStale(
                1L,
                10L,
                "sensor-1",
                OffsetDateTime.parse("2026-08-20T10:03:00+09:00")
        )).isTrue();
    }

    @Test
    @DisplayName("다른 Sensor의 최신 시각은 서로 간섭하지 않습니다")
    void watermarkIsSeparatedBySensorTest() {
        detector.isStale(
                1L,
                10L,
                "sensor-1",
                OffsetDateTime.parse("2026-08-20T10:05:00+09:00")
        );

        assertThat(detector.isStale(
                1L,
                10L,
                "sensor-2",
                OffsetDateTime.parse("2026-08-20T10:03:00+09:00")
        )).isFalse();
    }
}
