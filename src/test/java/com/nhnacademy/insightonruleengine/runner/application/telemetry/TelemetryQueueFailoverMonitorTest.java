package com.nhnacademy.insightonruleengine.runner.application.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nhnacademy.insightonruleengine.config.TelemetryRoutingProperties;
import com.nhnacademy.insightonruleengine.heartbeat.EngineHeartbeatService;
import com.nhnacademy.insightonruleengine.heartbeat.EngineStatus;
import com.nhnacademy.insightonruleengine.heartbeat.HeartbeatProperties;
import com.nhnacademy.insightonruleengine.runner.infrastructure.inbound.rabbitmq.TelemetryListenerContainerManager;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;

@ExtendWith(MockitoExtension.class)
class TelemetryQueueFailoverMonitorTest {

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
                "insighton.core.telemetry.exchange-v2",
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
        TelemetryQueueFailoverMonitor monitor = new TelemetryQueueFailoverMonitor(
                engineHeartbeatService,
                listenerContainerManager,
                enabledRoutingProperties,
                enabledHeartbeatProperties
        );

        when(engineHeartbeatService.getEngineStatus()).thenReturn(EngineStatus.DOWN);
        when(listenerContainerManager.isTakingOver()).thenReturn(false);

        monitor.checkPeerStatus();

        verify(listenerContainerManager).takeover();
        verify(listenerContainerManager, never()).handback();
    }

    @Test
    @DisplayName("상대 Engine이 복구(UP)되었고 현재 인계 중이면 handback을 호출합니다.")
    void peerUpTest() {
        TelemetryQueueFailoverMonitor monitor = new TelemetryQueueFailoverMonitor(
                engineHeartbeatService,
                listenerContainerManager,
                enabledRoutingProperties,
                enabledHeartbeatProperties
        );

        when(engineHeartbeatService.getEngineStatus()).thenReturn(EngineStatus.UP);
        when(listenerContainerManager.isTakingOver()).thenReturn(true);

        monitor.checkPeerStatus();

        verify(listenerContainerManager).handback();
        verify(listenerContainerManager, never()).takeover();
    }

    @Test
    @DisplayName("Redis 연결 장애 시에는 상대 장애로 오판하지 않고 takeover를 호출하지 않습니다.")
    void redisFailureTest() {
        TelemetryQueueFailoverMonitor monitor = new TelemetryQueueFailoverMonitor(
                engineHeartbeatService,
                listenerContainerManager,
                enabledRoutingProperties,
                enabledHeartbeatProperties
        );

        when(engineHeartbeatService.getEngineStatus()).thenThrow(
                new RedisConnectionFailureException("Redis is unreachable")
        );

        monitor.checkPeerStatus();

        verify(listenerContainerManager, never()).takeover();
        verify(listenerContainerManager, never()).handback();
    }

    @Test
    @DisplayName("큐 인계 실패는 하트비트 실패와 구분하고 반복 오류를 억제합니다.")
    void takeoverFailureIsLoggedOnceAndRecoveryIsReported() {
        TelemetryQueueFailoverMonitor monitor = new TelemetryQueueFailoverMonitor(
                engineHeartbeatService,
                listenerContainerManager,
                enabledRoutingProperties,
                enabledHeartbeatProperties
        );
        when(engineHeartbeatService.getEngineStatus()).thenReturn(EngineStatus.DOWN);
        when(listenerContainerManager.isTakingOver()).thenReturn(false);
        doThrow(new IllegalStateException("RabbitMQ unavailable"))
                .doThrow(new IllegalStateException("RabbitMQ unavailable"))
                .doNothing()
                .when(listenerContainerManager).takeover();

        Logger logger = (Logger) LoggerFactory.getLogger(TelemetryQueueFailoverMonitor.class);
        Level originalLevel = logger.getLevel();
        boolean originalAdditive = logger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        appender.start();
        logger.addAppender(appender);
        try {
            monitor.checkPeerStatus();
            monitor.checkPeerStatus();
            monitor.checkPeerStatus();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(originalLevel);
            logger.setAdditive(originalAdditive);
        }

        assertEquals(1L, appender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .filter(event -> event.getFormattedMessage().startsWith("Telemetry 큐 전환에 실패했습니다."))
                .count());
        assertEquals(1L, appender.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .filter(event -> event.getFormattedMessage().startsWith("Telemetry 큐 전환 재시도가 성공했습니다."))
                .count());
        assertEquals(0L, appender.list.stream()
                .filter(event -> event.getFormattedMessage().contains("하트비트 확인에 실패했습니다."))
                .count());
        verify(engineHeartbeatService, times(3)).getEngineStatus();
        verify(listenerContainerManager, times(3)).takeover();
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
        TelemetryQueueFailoverMonitor monitor = new TelemetryQueueFailoverMonitor(
                engineHeartbeatService,
                listenerContainerManager,
                disabledRouting,
                enabledHeartbeatProperties
        );

        monitor.checkPeerStatus();

        verify(engineHeartbeatService, never()).getEngineStatus();
        verify(listenerContainerManager, never()).takeover();
    }
}
