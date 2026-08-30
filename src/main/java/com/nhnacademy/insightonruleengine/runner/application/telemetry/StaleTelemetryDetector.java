package com.nhnacademy.insightonruleengine.runner.application.telemetry;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

//기기 별 마지막 Telemetry 발생 시각을 추적해 역순 패킷을 판정합니다.
@Component
public class StaleTelemetryDetector {

    private static final long DEFAULT_MAX_TRACKED_SENSORS = 10_000L;
    private static final Duration DEFAULT_IDLE_TTL = Duration.ofHours(24);

    private final Cache<SensorKey, Instant> latestTimestamps;

    public StaleTelemetryDetector() {
        this(DEFAULT_MAX_TRACKED_SENSORS, DEFAULT_IDLE_TTL);
    }

    @Autowired
    public StaleTelemetryDetector(
            @Value("${rule-engine.telemetry-stale.max-tracked-sensors:10000}") long maxTrackedSensors,
            @Value("${rule-engine.telemetry-stale.idle-ttl:24h}") Duration idleTtl
    ) {
        if (maxTrackedSensors <= 0L) {
            throw new IllegalArgumentException("최대 추적 센서 수는 양수여야 합니다.");
        }
        if (idleTtl == null || idleTtl.isZero() || idleTtl.isNegative()) {
            throw new IllegalArgumentException("Telemetry watermark 유휴 TTL은 양수여야 합니다.");
        }
        this.latestTimestamps = Caffeine.newBuilder()
                .maximumSize(maxTrackedSensors)
                .expireAfterAccess(idleTtl)
                .build();
    }

    // 현재 수신된 메시지의 time이 이전에 처리된 time보다 과거거나 동일하면 true
    public boolean isStale(Long groupId, Long locationId, Long sensorId, Instant eventTime) {
        if (groupId == null || locationId == null || sensorId == null || eventTime == null) {
            return false;
        }
        SensorKey key = new SensorKey(groupId, locationId, sensorId);
        AtomicBoolean stale = new AtomicBoolean();

        latestTimestamps.asMap().compute(key, (ignored, currentLatest) -> {
            if (currentLatest == null) {
                return eventTime;
            }
            if (eventTime.isAfter(currentLatest)) {
                return eventTime;
            }
            stale.set(true);
            return currentLatest;
        });
        return stale.get();
    }

    //테스트 격리 및 초기화를 위해 메모리 상태를 정리합니다.
    public void clear() {
        latestTimestamps.invalidateAll();
    }

    long trackedSensorCount() {
        latestTimestamps.cleanUp();
        return latestTimestamps.estimatedSize();
    }

    private record SensorKey(Long groupId, Long locationId, Long sensorId) {
    }
}
