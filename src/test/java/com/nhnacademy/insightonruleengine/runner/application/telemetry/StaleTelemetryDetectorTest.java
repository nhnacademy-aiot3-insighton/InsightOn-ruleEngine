package com.nhnacademy.insightonruleengine.runner.application.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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

    @Test
    @DisplayName("더 최신 Telemetry가 오면 watermark를 갱신합니다")
    void newerTelemetryUpdatesWatermarkTest() {
        detector.isStale(1L, 10L, 101L, Instant.parse("2026-08-20T01:05:00Z"));

        assertThat(detector.isStale(
                1L, 10L, 101L, Instant.parse("2026-08-20T01:06:00Z")))
                .isFalse();
        assertThat(detector.isStale(
                1L, 10L, 101L, Instant.parse("2026-08-20T01:05:30Z")))
                .isTrue();
    }

    @Test
    @DisplayName("식별자나 발생 시각이 없으면 stale로 단정하지 않습니다")
    void incompleteTelemetryIsNotStaleTest() {
        Instant timestamp = Instant.parse("2026-08-20T01:05:00Z");

        assertThat(detector.isStale(null, 10L, 101L, timestamp)).isFalse();
        assertThat(detector.isStale(1L, null, 101L, timestamp)).isFalse();
        assertThat(detector.isStale(1L, 10L, null, timestamp)).isFalse();
        assertThat(detector.isStale(1L, 10L, 101L, null)).isFalse();
    }

    @Test
    @DisplayName("초기화하면 이전 watermark를 사용하지 않습니다")
    void clearRemovesWatermarkTest() {
        Instant timestamp = Instant.parse("2026-08-20T01:05:00Z");
        detector.isStale(1L, 10L, 101L, timestamp);

        detector.clear();

        assertThat(detector.isStale(1L, 10L, 101L, timestamp)).isFalse();
    }

    @Test
    @DisplayName("추적하는 Sensor 상태는 설정된 최대 크기를 넘지 않습니다")
    void boundsTrackedSensorStateTest() {
        StaleTelemetryDetector boundedDetector =
                new StaleTelemetryDetector(2L, Duration.ofHours(1));

        boundedDetector.isStale(1L, 10L, 101L, Instant.parse("2026-08-20T01:05:00Z"));
        boundedDetector.isStale(1L, 10L, 102L, Instant.parse("2026-08-20T01:05:00Z"));
        boundedDetector.isStale(1L, 10L, 103L, Instant.parse("2026-08-20T01:05:00Z"));

        assertThat(boundedDetector.trackedSensorCount()).isEqualTo(2L);
    }
}
