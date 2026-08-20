package com.nhnacademy.insightonruleengine.runner.dto;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SensorEventTest {

    @Test
    void rejectsMissingRequiredFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(null, 10L, 100L, Map.of("temperature", 20), Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(1L, 10L, 100L, Map.of(), Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> new SensorEvent(1L, 10L, 100L, Map.of(" ", 20), Instant.now()));
    }
}
