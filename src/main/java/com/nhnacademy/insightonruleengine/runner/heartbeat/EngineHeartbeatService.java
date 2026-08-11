package com.nhnacademy.insightonruleengine.runner.heartbeat;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EngineHeartbeatService {

    private final EngineHeartbeatRepository heartbeatRepository;
    private final HeartbeatProperties heartbeatProperties;

    //현재 엔진이 살아있음을 알리기 위해 heartbeat 만료시간을 늘려줍니다.
    public void refreshHeartbeat() {
        heartbeatRepository.refresh(heartbeatProperties.engineId(), heartbeatProperties.ttl());
    }

    //상대 heartbeat key가 있으면 UP, 없으면 DOWN으로 변환합니다.
    public EngineStatus getEngineStatus() {
        if (heartbeatRepository.isHeartbeat(heartbeatProperties.peerEngineId())) {
            return EngineStatus.UP;
        }
        return EngineStatus.DOWN;
    }
}
