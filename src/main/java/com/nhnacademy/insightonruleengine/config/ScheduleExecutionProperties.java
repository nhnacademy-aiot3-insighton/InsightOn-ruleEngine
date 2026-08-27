package com.nhnacademy.insightonruleengine.config;

import java.time.Duration;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "rule-engine.schedule")
public record ScheduleExecutionProperties(
        @DefaultValue("Asia/Seoul") String zone,
        @DefaultValue("PT10M") Duration executionKeyTtl,
        @DefaultValue("2") int poolSize
) {

    public ScheduleExecutionProperties {
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("Schedule zone은 필수입니다.");
        }
        try {
            ZoneId.of(zone);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Schedule zone이 올바르지 않습니다: " + zone, exception);
        }
        if (executionKeyTtl == null || executionKeyTtl.isZero() || executionKeyTtl.isNegative()) {
            throw new IllegalArgumentException("Schedule executionKeyTtl은 양수여야 합니다.");
        }
        if (poolSize <= 0) {
            throw new IllegalArgumentException("Schedule poolSize는 양수여야 합니다.");
        }
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }
}
