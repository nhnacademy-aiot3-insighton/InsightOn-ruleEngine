package com.nhnacademy.insightonruleengine.runner.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SensorEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void rejectsMissingRequiredFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(null, 10L, 100L, Map.of("temperature", 20), Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(1L, 10L, 100L, Map.of(), Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(1L, 10L, 100L, Map.of(" ", 20), Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(0L, 10L, 100L, Map.of("temperature", 20), Instant.now()));
    }

    @Test
    void deserializesLegacyTelemetryFieldNamesWithoutIntermediateDto() throws Exception {
        String json = """
                {
                    "groupId": 1,
                    "locationId": 10,
                    "sensorId": "100",
                    "metrics": {"temperature": 25.5},
                    "time": "2026-08-17T12:00:00Z"
                }
                """;

        SensorEvent event = objectMapper.readValue(json, SensorEvent.class);

        assertEquals(100L, event.sensorId());
        assertEquals(Instant.parse("2026-08-17T12:00:00Z"), event.timestamp());
    }
}
