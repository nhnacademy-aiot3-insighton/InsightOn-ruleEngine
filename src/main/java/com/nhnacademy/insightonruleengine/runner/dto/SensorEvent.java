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
        metrics = metrics == null ? null : Map.copyOf(metrics);
    }
}
