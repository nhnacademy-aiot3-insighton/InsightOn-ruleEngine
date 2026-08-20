package com.nhnacademy.insightonruleengine.runner.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

public record SensorEvent(
        @NotNull Long groupId,
        @NotNull Long locationId,
        @NotNull Long sensorId,
        @NotEmpty Map<String, Object> metrics,
        @NotNull Instant timestamp
) {
}
