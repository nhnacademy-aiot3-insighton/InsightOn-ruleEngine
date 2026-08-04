package com.nhnacademy.insightonruleengine.runner.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

public record SensorEvent(
        Long deviceId,
        String deviceEui,
        Long locationId,
        OffsetDateTime occurredAt,
        JsonNode payload
) {
}
