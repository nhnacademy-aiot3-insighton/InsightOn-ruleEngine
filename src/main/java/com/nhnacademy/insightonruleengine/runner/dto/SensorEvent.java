package com.nhnacademy.insightonruleengine.runner.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Core가 AMQ로 발행하는 센서 텔레메트리 이벤트 메시지입니다.
 */
public record SensorEvent(
        @NotNull @Positive Long groupId,
        @NotNull @Positive Long locationId,
        @NotNull @Positive Long sensorId,
        @NotEmpty Map<String, Object> metrics,
        @NotNull @JsonAlias("time") Instant timestamp
) {

    private static final int MAX_METRIC_COUNT = 256;
    private static final int MAX_METRIC_KEY_LENGTH = 100;

    public SensorEvent {
        if (groupId == null || locationId == null || sensorId == null || timestamp == null) {
            throw new IllegalArgumentException("센서 이벤트의 식별자와 timestamp는 필수입니다.");
        }
        if (groupId <= 0 || locationId <= 0 || sensorId <= 0) {
            throw new IllegalArgumentException("센서 이벤트의 식별자는 양수여야 합니다.");
        }
        if (metrics == null || metrics.isEmpty()) {
            throw new IllegalArgumentException("센서 이벤트 metrics는 비어 있을 수 없습니다.");
        }
        if (metrics.size() > MAX_METRIC_COUNT) {
            throw new IllegalArgumentException("센서 이벤트 metric 개수가 허용 범위를 초과했습니다.");
        }
        if (metrics.keySet().stream().anyMatch(key -> key == null
                || key.isBlank()
                || key.length() > MAX_METRIC_KEY_LENGTH)) {
            throw new IllegalArgumentException("센서 이벤트 metric key가 올바르지 않습니다.");
        }
        if (metrics.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("센서 이벤트 metric value는 null일 수 없습니다.");
        }
        metrics = Map.copyOf(metrics);
    }
}
