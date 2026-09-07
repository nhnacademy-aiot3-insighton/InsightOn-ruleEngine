package com.nhnacademy.insightonruleengine.heartbeat;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "rule-engine.heartbeat")
public record HeartbeatProperties(
        boolean enabled,
        String engineId,
        String peerEngineId,
        Duration refreshInterval,
        Duration ttl,
        @DefaultValue("5") int requiredConsecutiveUpChecks
) {
    private static final Duration REQUIRED_REFRESH_INTERVAL = Duration.ofSeconds(5);
    private static final Duration REQUIRED_TTL = Duration.ofSeconds(15);

    //엔진 이름과 시간 설정이 잘못되었는지 검증합니다.
    public void validateConfiguration() {
        if (!enabled) {
            return;
        }
        if (engineId == null
                || engineId.isBlank()
                || peerEngineId == null
                || peerEngineId.isBlank()
                || engineId.equals(peerEngineId)) {
            throw new IllegalStateException("서로 다른 현재 엔진과 상대 엔진의 아이디가 필요합니다.");
        }
        if (!REQUIRED_REFRESH_INTERVAL.equals(refreshInterval)) {
            throw new IllegalStateException("heartbeat 갱신 주기는 5초여야 합니다.");
        }
        if (!REQUIRED_TTL.equals(ttl)) {
            throw new IllegalStateException("heartbeat TTL은 15초여야 합니다.");
        }
        if (requiredConsecutiveUpChecks < 1) {
            throw new IllegalStateException("복구 확인 연속 횟수는 1 이상이어야 합니다.");
        }
    }
}
