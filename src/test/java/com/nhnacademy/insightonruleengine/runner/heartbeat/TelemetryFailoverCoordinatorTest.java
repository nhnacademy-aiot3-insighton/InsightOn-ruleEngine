package com.nhnacademy.insightonruleengine.runner.heartbeat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.common.config.TelemetryRoutingProperties;
import com.nhnacademy.insightonruleengine.runner.messaging.TelemetryListenerContainerManager;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

@ExtendWith(MockitoExtension.class)
class TelemetryFailoverCoordinatorTest {

    @Mock
    private EngineHeartbeatService engineHeartbeatService;

    @Mock
    private TelemetryListenerContainerManager listenerContainerManager;

    private TelemetryRoutingProperties enabledRoutingProperties;
    private HeartbeatProperties enabledHeartbeatProperties;

    @BeforeEach
    void setUp() {
        enabledRoutingProperties = new TelemetryRoutingProperties(
                true,
                "insighton.core.telemetry.exchange",
                "telemetry.",
                List.of(0, 2, 4, 6, 8, 10, 12, 14),
                null
        );
        enabledHeartbeatProperties = new HeartbeatProperties(
                true,
                "engine-a",
                "engine-b",
                Duration.ofSeconds(5),
                Duration.ofSeconds(15)
        );
    }

    @Test
    @DisplayName("상대 Engine이 DOWN 상태이고 아직 인계 전이면 takeover를 호출합니다.")
    void peerDownTest() {
        TelemetryFailoverCoordinator coordinator = new TelemetryFailoverCoordinator(
                engineHeartbeatService,
                listenerContainerManager,
                enabledRoutingProperties,
                enabledHeartbeatProperties
        );

        when(engineHeartbeatService.getEngineStatus()).thenReturn(EngineStatus.DOWN);
        when(listenerContainerManager.isTakingOver()).thenReturn(false);

        coordinator.coordinate();

        verify(listenerContainerManager).takeover();
        verify(listenerContainerManager, never()).handback();
    }

    @Test
    @DisplayName("상대 Engine이 복구(UP)되었고 현재 인계 중이면 handback을 호출합니다.")
    void peerUpTest() {
        TelemetryFailoverCoordinator coordinator = new TelemetryFailoverCoordinator(
                engineHeartbeatService,
                listenerContainerManager,
                enabledRoutingProperties,
                enabledHeartbeatProperties
        );

        when(engineHeartbeatService.getEngineStatus()).thenReturn(EngineStatus.UP);
        when(listenerContainerManager.isTakingOver()).thenReturn(true);

        coordinator.coordinate();

        verify(listenerContainerManager).handback();
        verify(listenerContainerManager, never()).takeover();
    }

    @Test
    @DisplayName("Redis 연결 장애 시에는 상대 장애로 오판하지 않고 takeover를 호출하지 않습니다.")
    void redisFailureTest() {
        TelemetryFailoverCoordinator coordinator = new TelemetryFailoverCoordinator(
                engineHeartbeatService,
                listenerContainerManager,
                enabledRoutingProperties,
                enabledHeartbeatProperties
        );

        when(engineHeartbeatService.getEngineStatus()).thenThrow(
                new RedisConnectionFailureException("Redis is unreachable")
        );

        coordinator.coordinate();

        verify(listenerContainerManager, never()).takeover();
        verify(listenerContainerManager, never()).handback();
    }

    @Test
    @DisplayName("설정이 비활성화되어 있으면 상태 조회를 수행하지 않습니다.")
    void disabledConfigTest() {
        TelemetryRoutingProperties disabledRouting = new TelemetryRoutingProperties(
                false,
                "exchange",
                "prefix.",
                List.of(0, 2, 4, 6, 8, 10, 12, 14),
                null
        );
        TelemetryFailoverCoordinator coordinator = new TelemetryFailoverCoordinator(
                engineHeartbeatService,
                listenerContainerManager,
                disabledRouting,
                enabledHeartbeatProperties
        );

        coordinator.coordinate();

        verify(engineHeartbeatService, never()).getEngineStatus();
        verify(listenerContainerManager, never()).takeover();
    }
}
