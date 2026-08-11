package com.nhnacademy.insightonruleengine.runner.heartbeat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class HeartbeatScheduler {

    private final EngineHeartbeatService engineHeartbeatService;

    //현재 엔진의 heartbeat가 만료되기 전에 갱신하게 해줍니다.
    @Scheduled(fixedDelayString = "${rule-engine.heartbeat.refresh-interval}")
    public void refreshHeartbeat() {
        engineHeartbeatService.refreshHeartbeat();
    }
}
