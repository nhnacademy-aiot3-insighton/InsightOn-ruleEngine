package com.nhnacademy.insightonruleengine.heartbeat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class HeartbeatScheduler {

    private final EngineHeartbeatService engineHeartbeatService;
    private boolean refreshFailed;

    //현재 엔진의 heartbeat가 만료되기 전에 갱신하게 해줍니다.
    @Scheduled(fixedDelayString = "${rule-engine.heartbeat.refresh-interval}")
    public void refreshHeartbeat() {
        try {
            engineHeartbeatService.refreshHeartbeat();
            if (refreshFailed) {
                log.info("현재 엔진의 하트비트 갱신이 복구됐습니다.");
                refreshFailed = false;
            }
        } catch (RuntimeException exception) {
            if (!refreshFailed) {
                log.warn(
                        "현재 엔진의 하트비트 갱신에 실패했습니다. errorType={}, message={}",
                        exception.getClass().getSimpleName(),
                        exception.getMessage()
                );
                log.debug("현재 엔진 하트비트 갱신 실패 상세.", exception);
                refreshFailed = true;
            }
        }
    }
}
