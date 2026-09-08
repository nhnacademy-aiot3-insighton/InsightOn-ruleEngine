package com.nhnacademy.insightonruleengine.runner.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SensorEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void rejectsMissingRequiredFields() {
        Instant timestamp = Instant.now();
        Map<String, Object> metrics = Map.of("temperature", 20);

        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(null, 10L, 100L, metrics, timestamp));
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(1L, null, 100L, metrics, timestamp));
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(1L, 10L, null, metrics, timestamp));
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(1L, 10L, 100L, metrics, null));
    }

    @Test
    void rejectsNonPositiveIdentifiers() {
        Instant timestamp = Instant.now();
        Map<String, Object> metrics = Map.of("temperature", 20);

        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(0L, 10L, 100L, metrics, timestamp));
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(1L, 0L, 100L, metrics, timestamp));
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(1L, 10L, 0L, metrics, timestamp));
    }

    @Test
    void rejectsInvalidMetrics() {
        Instant timestamp = Instant.now();
        Map<String, Object> nullKeyMetrics = new HashMap<>();
        nullKeyMetrics.put(null, 20);
        Map<String, Object> nullValueMetrics = new HashMap<>();
        nullValueMetrics.put("temperature", null);
        Map<String, Object> emptyMetrics = Map.of();
        Map<String, Object> blankKeyMetrics = Map.of(" ", 20);
        String oversizedKey = "x".repeat(101);
        Map<String, Object> oversizedKeyMetrics = Map.of(oversizedKey, 20);
        Map<String, Object> tooManyMetrics = new LinkedHashMap<>();
        for (int index = 0; index < 257; index++) {
            tooManyMetrics.put("metric-" + index, index);
        }

        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(1L, 10L, 100L, null, timestamp));
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(1L, 10L, 100L, emptyMetrics, timestamp));
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(1L, 10L, 100L, tooManyMetrics, timestamp));
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(1L, 10L, 100L, nullKeyMetrics, timestamp));
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(1L, 10L, 100L, blankKeyMetrics, timestamp));
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(1L, 10L, 100L, oversizedKeyMetrics, timestamp));
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(1L, 10L, 100L, nullValueMetrics, timestamp));
    }

    @Test
    void copiesMetricsToProtectEventFromExternalMutation() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("temperature", 20);

        SensorEvent event = new SensorEvent(1L, 10L, 100L, metrics, Instant.now());
        metrics.put("temperature", 30);

        assertNotSame(metrics, event.metrics());
        assertEquals(20, event.metrics().get("temperature"));
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
