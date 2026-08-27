package com.nhnacademy.insightonruleengine.runner.application.telemetry;

import com.nhnacademy.insightonruleengine.config.TelemetryRoutingProperties;
import com.nhnacademy.insightonruleengine.heartbeat.EngineHeartbeatService;
import com.nhnacademy.insightonruleengine.heartbeat.EngineStatus;
import com.nhnacademy.insightonruleengine.heartbeat.HeartbeatProperties;
import com.nhnacademy.insightonruleengine.runner.infrastructure.inbound.rabbitmq.TelemetryListenerContainerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 상대 Engine의 Redis Heartbeat 상태를 주기적으로 감시하여 상대 장애 시 큐 인계를,
// 상대 복구 시 인계 큐 반환(Handback)을 수행합니다.
// Redis 접속 장애 시에는 상대 장애로 오판하여 큐 인계를 시작하지 않도록 분리합니다.
@Slf4j
@Component
@ConditionalOnBean({EngineHeartbeatService.class, TelemetryListenerContainerManager.class})
@ConditionalOnProperty(prefix = "rule-engine.telemetry-routing", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TelemetryQueueFailoverMonitor {

    private final EngineHeartbeatService engineHeartbeatService;
    private final TelemetryListenerContainerManager listenerContainerManager;
    private final TelemetryRoutingProperties routingProperties;
    private final HeartbeatProperties heartbeatProperties;
    private boolean heartbeatCheckFailed;

    @Scheduled(fixedDelayString = "${rule-engine.heartbeat.failover-check-interval:5000}")
    public void checkPeerStatus() {
        if (!routingProperties.enabled() || !heartbeatProperties.enabled()) {
            return;
        }
        try {
            EngineStatus engineStatus = engineHeartbeatService.getEngineStatus();
            if (heartbeatCheckFailed) {
                log.info("상대 엔진의 heartbeat 확인이 복구됐습니다.");
                heartbeatCheckFailed = false;
            }
            if (engineStatus == EngineStatus.DOWN && !listenerContainerManager.isTakingOver()) {
                log.warn("상대 엔진이 DOWN 상태입니다.");
                listenerContainerManager.takeover();
            } else if (engineStatus == EngineStatus.UP && listenerContainerManager.isTakingOver()) {
                log.info("상대 엔진이 회복됐습니다.");
                listenerContainerManager.handback();
            }
        } catch (RuntimeException exception) {
            if (!heartbeatCheckFailed) {
                log.warn(
                        "상대 엔진의 heartbeat 확인에 실패했습니다. errorType={}, message={}",
                        exception.getClass().getSimpleName(),
                        exception.getMessage()
                );
                log.debug("상대 엔진 heartbeat 확인 실패 상세.", exception);
                heartbeatCheckFailed = true;
            }
        }
    }
}
