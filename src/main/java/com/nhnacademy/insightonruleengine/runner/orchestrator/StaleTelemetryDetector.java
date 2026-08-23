package com.nhnacademy.insightonruleengine.runner.orchestrator;

import java.time.OffsetDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

//기기 별 마지막 Telemetry 발생 시각을 추적해 역순 패킷을 판정합니다.
@Component
public class StaleTelemetryDetector {

    private final ConcurrentMap<String, OffsetDateTime> latestTimestamps = new ConcurrentHashMap<>();

    // 현재 수신된 메시지의 time이 이전에 처리된 time보다 과거거나 동일하면 true
    public boolean isStale(Long groupId, Long locationId, String sensorId, OffsetDateTime eventTime){
        if (groupId == null || locationId == null || sensorId == null || eventTime == null) {
            return false;
        }
        String key = generateKey(groupId, locationId, sensorId);
        final boolean[] staleHolder = new boolean[1];

        latestTimestamps.compute(key, (k, currentLatest) -> {
            if (currentLatest == null) {
                staleHolder[0] = false;
                return eventTime;
            }
            if (eventTime.isAfter(currentLatest)) {
                staleHolder[0] = false;
                return eventTime;
            }
            staleHolder[0] = true;
            return currentLatest;
        });
        return staleHolder[0];
    }


    //테스트 격리 및 초기화를 위해 메모리 상태를 정리합니다.
    public void clear() {
        latestTimestamps.clear();
    }

    private String generateKey(Long groupId, Long locationId, String sensorId){
        return groupId + ":" + locationId + ":" + sensorId;
    }
}
