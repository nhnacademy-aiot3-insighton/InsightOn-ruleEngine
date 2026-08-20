package com.nhnacademy.insightonruleengine.runner.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * Core가 AMQ로 발행하는 센서 텔레메트리 이벤트 메시지입니다.
 */
public record SensorEvent(
        @NotNull Long groupId,
        @NotNull Long locationId,
        @NotNull Long sensorId,
        @NotEmpty Map<String, Object> metrics,
        @NotNull Instant timestamp
) {

    public SensorEvent {
        if (groupId == null || locationId == null || sensorId == null || timestamp == null) {
            throw new IllegalArgumentException("센서 이벤트의 식별자와 timestamp는 필수입니다.");
        }
        if (metrics == null || metrics.isEmpty()) {
            throw new IllegalArgumentException("센서 이벤트 metrics는 비어 있을 수 없습니다.");
        }
        if (metrics.keySet().stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalArgumentException("센서 이벤트 metric key는 비어 있을 수 없습니다.");
        }
        metrics = Map.copyOf(metrics);
    }
}
