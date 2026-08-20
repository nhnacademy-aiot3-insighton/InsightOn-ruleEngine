package com.nhnacademy.insightonruleengine.runner.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TelemetryEventMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("정상 Telemetry 메시지는 SensorEvent로 정상적으로 변환됩니다.")
    void validTelemetryAndSensorEventTest() {
        OffsetDateTime now = OffsetDateTime.of(2026, 8, 17, 12, 0, 0, 0, ZoneOffset.UTC);
        TelemetryEventMessage message = new TelemetryEventMessage(
                1L,
                100L,
                "1001",
                Map.of("temperature", 25.5, "humidity", 60),
                now
        );
        message.validate();
        SensorEvent event = message.toSensorEvent(objectMapper);
        assertEquals(1L, event.groupId());
        assertEquals(100L, event.locationId());
        assertEquals(1001L, event.sensorId());
        assertEquals(now.toInstant(), event.timestamp());
        assertEquals(25.5, event.metrics().get("temperature"));
        assertEquals(60, event.metrics().get("humidity"));
    }

    @Test
    @DisplayName("sensorId가 null이거나 공백이면 유효성 검증에 실패함을 증명하는 테스트")
    void missingSensorIdTest() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TelemetryEventMessage message = new TelemetryEventMessage(
                1L,
                100L,
                null,
                Map.of("temperature", 25.5, "humidity", 60),
                now
        );
        TelemetryEventMessage blankSensorId = new TelemetryEventMessage(
                1L,
                100L,
                "  ",
                Map.of("temperature", 25.5, "humidity", 60),
                now
        );
        assertThrows(IllegalArgumentException.class, message::validate);
        assertThrows(IllegalArgumentException.class, blankSensorId::validate);
    }

    @Test
    @DisplayName("groupId 또는 locationId가 null이면 유효성 검증에 실패하는 테스트")
    void missingGroupIdOrLocationIdTest() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TelemetryEventMessage message = new TelemetryEventMessage(
                null,
                100L,
                "sensor-node-01",
                Map.of("temperature", 25.5, "humidity", 60),
                now
        );
        TelemetryEventMessage missingLocation = new TelemetryEventMessage(
                1L,
                null,
                "sensor-node-01",
                Map.of("temperature", 25.5, "humidity", 60),
                now
        );
        assertThrows(IllegalArgumentException.class, message::validate);
        assertThrows(IllegalArgumentException.class, missingLocation::validate);
    }

    @Test
    @DisplayName("metrics가 null이거나 비어있으면 유효성 검증에 실패하는 테스트")
    void emptyMetricsValidationTest() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TelemetryEventMessage message = new TelemetryEventMessage(
                1L,
                100L,
                "sensor-node-01",
                Map.of(),
                now
        );
        assertThrows(IllegalArgumentException.class, message::validate);
    }

    @Test
    @DisplayName("Json 문자열로부터 TelemetryEventMessage 역직렬화가 정상 동작하는 테스트")
    void JsonTest() throws Exception {
        String json = """
                {
                    "groupId": 10,
                    "locationId": 200,
                    "sensorId": "sensor-01",
                    "metrics": {
                        "temperature": 23.4,
                        "co2": 450
                    },
                    "time": "2026-08-17T12:00:00Z"
                }
                """;
        TelemetryEventMessage message = objectMapper.readValue(json, TelemetryEventMessage.class);
        message.validate();

        assertEquals(10L, message.groupId());
        assertEquals(200L, message.locationId());
        assertEquals("sensor-01", message.sensorId());
        assertEquals(23.4, message.metrics().get("temperature"));
        assertEquals(450, message.metrics().get("co2"));
    }
}
