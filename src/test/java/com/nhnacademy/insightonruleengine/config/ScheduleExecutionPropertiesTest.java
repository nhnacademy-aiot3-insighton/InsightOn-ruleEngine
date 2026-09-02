package com.nhnacademy.insightonruleengine.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ScheduleExecutionPropertiesTest {

    @Test
    void exposesValidatedZoneAndRuntimeLimits() {
        ScheduleExecutionProperties properties = new ScheduleExecutionProperties(
                "Asia/Seoul",
                Duration.ofMinutes(10),
                2
        );

        assertEquals(ZoneId.of("Asia/Seoul"), properties.zoneId());
        assertEquals(Duration.ofMinutes(10), properties.executionKeyTtl());
        assertEquals(2, properties.poolSize());
    }

    @Test
    void rejectsInvalidConfiguration() {
        Duration oneMinute = Duration.ofMinutes(1);
        Duration oneNano = Duration.ofNanos(1);

        assertThrows(IllegalArgumentException.class,
                () -> new ScheduleExecutionProperties(" ", oneMinute, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ScheduleExecutionProperties("Unknown/Zone", oneMinute, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ScheduleExecutionProperties("Asia/Seoul", Duration.ZERO, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ScheduleExecutionProperties("Asia/Seoul", oneNano, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ScheduleExecutionProperties("Asia/Seoul", oneMinute, 0));
    }
}
