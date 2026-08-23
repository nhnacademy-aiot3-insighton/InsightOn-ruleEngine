package com.nhnacademy.insightonruleengine.heartbeat;

import com.nhnacademy.insightonruleengine.config.TelemetryRoutingProperties;
import com.nhnacademy.insightonruleengine.runner.messaging.TelemetryListenerContainerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 상대 Engine의 Redis Heartbeat 상태를 주기적으로 감시하여 상대 장애 시 큐 인계를,
// 상대 복구 시 인계 큐 반환(Handback)을 조율합니다.
// Redis 접속 장애 시에는 상대 장애로 오판하여 큐 인계를 시작하지 않도록 분리합니다.
@Slf4j
@Component
@ConditionalOnBean({EngineHeartbeatService.class, TelemetryListenerContainerManager.class})
@ConditionalOnProperty(prefix = "rule-engine.telemetry-routing", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TelemetryFailoverCoordinator {

    private final EngineHeartbeatService engineHeartbeatService;
    private final TelemetryListenerContainerManager listenerContainerManager;
    private final TelemetryRoutingProperties routingProperties;
    private final HeartbeatProperties heartbeatProperties;

    @Scheduled(fixedDelayString = "${rule-engine.heartbeat.failover-check-interval:5000}")
    public void coordinate(){
        if(!routingProperties.enabled() || !heartbeatProperties.enabled()) {
            return;
        }
        try{
            EngineStatus engineStatus = engineHeartbeatService.getEngineStatus();
            if (engineStatus == EngineStatus.DOWN && !listenerContainerManager.isTakingOver()) {
                log.warn("상대 엔진이 DOWN 상태입니다.");
                listenerContainerManager.takeover();
            } else if (engineStatus == EngineStatus.UP && listenerContainerManager.isTakingOver()) {
                log.info("상대 엔진이 회복됐습니다.");
                listenerContainerManager.handback();
            }
        }catch (Exception e){
            log.warn("상대 엔진의 heartbeat 체크에 실패했습니다.",e);
        }
    }
}
