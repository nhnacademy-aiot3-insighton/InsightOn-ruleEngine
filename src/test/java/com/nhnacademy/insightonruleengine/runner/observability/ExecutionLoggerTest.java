package com.nhnacademy.insightonruleengine.runner.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.runner.model.ExecutionTriggerType;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ExecutionLoggerTest {

    private final ExecutionLogger executionLogger = new ExecutionLogger();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ExecutionLogger.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;
    private boolean originalAdditive;

    @BeforeEach
    void setUp() {
        originalLevel = logger.getLevel();
        originalAdditive = logger.isAdditive();
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(originalLevel);
        logger.setAdditive(originalAdditive);
    }

    @Test
    @DisplayName("반복 라우팅 실패는 최초 한 번만 ERROR로 남기고 성공 시 억제 건수를 알립니다.")
    void suppressesRepeatedRoutingFailuresUntilRecovery() {
        SensorEvent event = sensorEvent();
        RuntimeException exception = new IllegalStateException("DB unavailable");

        executionLogger.routingFailed(event, exception);
        executionLogger.routingFailed(event, exception);
        executionLogger.eventRouted(event, 1);

        assertEquals(1L, count(Level.ERROR, "센서 이벤트 라우팅에 실패했습니다."));
        assertEquals(1L, count(Level.INFO, "센서 이벤트 라우팅이 복구됐습니다."));
        assertTrue(formattedMessages().stream()
                .anyMatch(message -> message.contains("suppressedFailureCount=1")));
    }

    @Test
    @DisplayName("다른 장소의 정상 라우팅은 장애 상태를 복구 처리하지 않습니다.")
    void routingRecoveryIsScopedByRoute() {
        SensorEvent failedRouteEvent = sensorEvent();
        SensorEvent healthyRouteEvent = new SensorEvent(
                2L,
                20L,
                200L,
                Map.of("temperature", 25.0),
                Instant.parse("2026-08-03T00:00:01Z")
        );

        executionLogger.routingFailed(
                failedRouteEvent,
                new IllegalStateException("DB unavailable")
        );
        executionLogger.eventRouted(healthyRouteEvent, 1);

        assertEquals(0L, count(Level.INFO, "센서 이벤트 라우팅이 복구됐습니다."));

        executionLogger.eventRouted(failedRouteEvent, 1);

        assertEquals(1L, count(Level.INFO, "센서 이벤트 라우팅이 복구됐습니다."));
    }

    @Test
    @DisplayName("동일한 플로우 실행 실패는 억제하고 복구 로그에 실행 문맥을 남깁니다.")
    void suppressesRepeatedFlowFailuresAndLogsContext() {
        ExecutionLogContext context = executionContext();
        NodeDefinition node = new NodeDefinition(
                2L,
                NodeType.ALERT,
                JsonNodeFactory.instance.objectNode()
        );
        RuntimeException exception = new IllegalStateException("RabbitMQ unavailable");

        executionLogger.flowFailed(context, node, exception);
        executionLogger.flowFailed(context, node, exception);
        executionLogger.flowFinished(context, node.nodeId(), true);

        assertEquals(1L, count(Level.ERROR, "플로우 실행 실패."));
        assertEquals(1L, count(Level.INFO, "플로우 실행이 복구됐습니다."));
        assertTrue(formattedMessages().stream().anyMatch(message ->
                message.contains("groupId=1")
                        && message.contains("locationId=10")
                        && message.contains("sensorId=100")
                        && message.contains("triggeredAt=2026-08-03T00:00:00Z")));
        assertTrue(formattedMessages().stream()
                .anyMatch(message -> message.contains("suppressedFailureCount=1")));
    }

    @Test
    @DisplayName("복구되지 않은 실패 상태는 설정된 최대 개수를 넘지 않습니다.")
    void boundsUnrecoveredFailureScopes() {
        ExecutionLogger boundedLogger = new ExecutionLogger(2);
        RuntimeException exception = new IllegalStateException("unavailable");

        boundedLogger.routingFailed(sensorEvent(1L, 10L), exception);
        boundedLogger.routingFailed(sensorEvent(2L, 20L), exception);
        boundedLogger.routingFailed(sensorEvent(3L, 30L), exception);
        boundedLogger.flowFailed(executionContext(100L), null, exception);
        boundedLogger.flowFailed(executionContext(200L), null, exception);
        boundedLogger.flowFailed(executionContext(300L), null, exception);

        assertEquals(2, boundedLogger.trackedRoutingFailureCount());
        assertEquals(2, boundedLogger.trackedFlowFailureCount());
    }

    private long count(Level level, String prefix) {
        return appender.list.stream()
                .filter(event -> event.getLevel() == level)
                .filter(event -> event.getFormattedMessage().startsWith(prefix))
                .count();
    }

    private List<String> formattedMessages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private SensorEvent sensorEvent() {
        return sensorEvent(1L, 10L);
    }

    private SensorEvent sensorEvent(Long groupId, Long locationId) {
        return new SensorEvent(
                groupId,
                locationId,
                100L,
                Map.of("temperature", 31.2),
                Instant.parse("2026-08-03T00:00:00Z")
        );
    }

    private ExecutionLogContext executionContext() {
        return executionContext(200L);
    }

    private ExecutionLogContext executionContext(Long flowId) {
        return new ExecutionLogContext(
                "execution-1",
                ExecutionTriggerType.TELEMETRY,
                flowId,
                1L,
                10L,
                100L,
                Instant.parse("2026-08-03T00:00:00Z")
        );
    }
}
