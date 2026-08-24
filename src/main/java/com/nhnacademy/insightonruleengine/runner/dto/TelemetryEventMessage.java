package com.nhnacademy.insightonruleengine.runner.dto;

import java.time.OffsetDateTime;
import java.util.Map;

//Core가 x-consistent-hash Exchange로 발행하는 외부 Telemetry 원본 메시지 모델입니다.
public record TelemetryEventMessage(
        Long groupId,
        Long locationId,
        String sensorId,
        Map<String, Object> metrics,
        OffsetDateTime time
) {
    public TelemetryEventMessage {
        metrics = metrics != null ? Map.copyOf(metrics) : Map.of();
    }

    //필수 필드(time, sensorId, groupId, locationId, metrics)의 유효성을 검증합니다.
    public void validate() {
        if (time == null) {
            throw new IllegalArgumentException("time은 필수입니다.");
        }
        if (sensorId == null || sensorId.isBlank()) {
            throw new IllegalArgumentException("sensorId는 필수입니다.");
        }
        if (groupId == null) {
            throw new IllegalArgumentException("groupId는 필수입니다.");
        }
        if (locationId == null) {
            throw new IllegalArgumentException("locationId는 필수입니다.");
        }
        if (metrics == null || metrics.isEmpty()) {
            throw new IllegalArgumentException("metrics는 필수입니다.");
        }
    }

    //내부 엔진 실행 모델인 SensorEvent로 변환합니다.
    public SensorEvent toSensorEvent() {
        validate();
        return new SensorEvent(
                groupId,
                locationId,
                Long.parseLong(sensorId),
                metrics,
                time.toInstant()
        );
    }
}
