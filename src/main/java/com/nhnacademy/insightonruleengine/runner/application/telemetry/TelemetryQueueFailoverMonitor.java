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
    private boolean queueTransitionFailed;

    @Scheduled(fixedDelayString = "${rule-engine.heartbeat.failover-check-interval:5000}")
    public void checkPeerStatus() {
        if (!routingProperties.enabled() || !heartbeatProperties.enabled()) {
            return;
        }
        EngineStatus engineStatus = getPeerEngineStatus();
        if (engineStatus == null) {
            return;
        }
        if (engineStatus == EngineStatus.DOWN && !listenerContainerManager.isTakingOver()) {
            transitionQueue("인계", listenerContainerManager::takeover);
        } else if (engineStatus == EngineStatus.UP && listenerContainerManager.isTakingOver()) {
            transitionQueue("반환", listenerContainerManager::handback);
        }
    }

    private EngineStatus getPeerEngineStatus() {
        try {
            EngineStatus engineStatus = engineHeartbeatService.getEngineStatus();
            if (heartbeatCheckFailed) {
                log.info("상대 엔진의 하트비트 확인이 복구됐습니다. peerEngineId={}",
                        heartbeatProperties.peerEngineId());
                heartbeatCheckFailed = false;
            }
            return engineStatus;
        } catch (RuntimeException exception) {
            if (!heartbeatCheckFailed) {
                log.warn(
                        "상대 엔진의 하트비트 확인에 실패했습니다. peerEngineId={}, errorType={}, message={}",
                        heartbeatProperties.peerEngineId(),
                        exception.getClass().getSimpleName(),
                        exception.getMessage()
                );
                log.debug("상대 엔진 하트비트 확인 실패 상세.", exception);
                heartbeatCheckFailed = true;
            }
            return null;
        }
    }

    private void transitionQueue(String action, Runnable transition) {
        try {
            transition.run();
            if (queueTransitionFailed) {
                log.info("Telemetry 큐 전환 재시도가 성공했습니다. peerEngineId={}, action={}",
                        heartbeatProperties.peerEngineId(), action);
                queueTransitionFailed = false;
            }
        } catch (RuntimeException exception) {
            if (!queueTransitionFailed) {
                log.error(
                        "Telemetry 큐 전환에 실패했습니다. peerEngineId={}, action={}, errorType={}, message={}",
                        heartbeatProperties.peerEngineId(),
                        action,
                        exception.getClass().getSimpleName(),
                        exception.getMessage(),
                        exception
                );
                queueTransitionFailed = true;
            }
        }
    }
}
