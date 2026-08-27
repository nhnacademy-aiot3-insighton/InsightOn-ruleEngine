package com.nhnacademy.insightonruleengine.runner.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.LinkDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.ActuatorControlParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.ExecutionTriggerType;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.execution.executor.NodeExecutor;
import com.nhnacademy.insightonruleengine.runner.execution.executor.NodeExecutorRegistry;
import com.nhnacademy.insightonruleengine.runner.observability.ExecutionLogContext;
import com.nhnacademy.insightonruleengine.runner.observability.ExecutionLogger;
import com.nhnacademy.insightonruleengine.runner.application.router.FlowRouter;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlowRunnerTest {

    @Test
    @DisplayName("Trigger부터 Link를 따라 Action까지 실행한다")
    void runSensorThresholdAlertPath() {
        List<NodeType> executed = new ArrayList<>();
        FlowRouter router = event -> List.of(flowDefinition());
        NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(
                executor(NodeType.SENSOR, NodeExecutionResult.next("out"), executed),
                executor(NodeType.THRESHOLD, NodeExecutionResult.next("true"), executed),
                executor(NodeType.ALERT, NodeExecutionResult.complete(), executed)
        ));
        FlowRunner runner = new FlowRunner(router, registry, new RecordingExecutionLogger());

        runner.run(sensorEvent());

        assertEquals(List.of(NodeType.SENSOR, NodeType.THRESHOLD, NodeType.ALERT), executed);
    }

    @Test
    @DisplayName("Filter가 false를 반환하고 false Link가 없으면 Action 없이 정상 종료한다")
    void filterFalseWithoutLinkFinishesNormally() {
        List<NodeType> executed = new ArrayList<>();
        FlowRouter router = event -> List.of(falseBranchFlowDefinition());
        NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(
                executor(NodeType.SENSOR, NodeExecutionResult.next("out"), executed),
                executor(NodeType.THRESHOLD, NodeExecutionResult.next("false"), executed)
        ));
        RecordingExecutionLogger logger = new RecordingExecutionLogger();
        FlowRunner runner = new FlowRunner(router, registry, logger);

        runner.run(sensorEvent());

        assertEquals(List.of(NodeType.SENSOR, NodeType.THRESHOLD), executed);
        assertEquals(2L, logger.terminalNodeId);
        assertFalse(logger.terminalActionReached);
    }

    @Test
    @DisplayName("Filter가 아닌 노드의 false 출력은 실행 계약 오류로 기록한다")
    void nonFilterFalseWithoutLinkFails() {
        List<NodeType> executed = new ArrayList<>();
        FlowRouter router = event -> List.of(singleSensorFlowDefinition());
        NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(
                executor(NodeType.SENSOR, NodeExecutionResult.next("false"), executed)
        ));
        RecordingExecutionLogger logger = new RecordingExecutionLogger();
        FlowRunner runner = new FlowRunner(router, registry, logger);

        runner.run(sensorEvent());

        assertEquals(List.of(NodeType.SENSOR), executed);
        assertNotNull(logger.failure);
        assertInstanceOf(IllegalStateException.class, logger.failure);
    }

    @Test
    @DisplayName("deviceId만 저장된 기존 Actuator 설정도 실행 경로에서 파싱된다")
    void legacyActuatorConfigurationRuns() {
        List<NodeType> executed = new ArrayList<>();
        NodeParamsParser parser = new NodeParamsParser(
                new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator());
        NodeExecutor legacyActuatorExecutor = new NodeExecutor() {
            @Override
            public NodeType supports() {
                return NodeType.ACTUATOR_CONTROL;
            }

            @Override
            public NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context) {
                ActuatorControlParams params = parser.parse(NodeType.ACTUATOR_CONTROL, node.configuration());
                assertEquals(900L, params.deviceId());
                executed.add(node.nodeType());
                return NodeExecutionResult.complete();
            }
        };
        NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(
                executor(NodeType.SENSOR, NodeExecutionResult.next("out"), executed),
                legacyActuatorExecutor
        ));
        FlowRunner runner = new FlowRunner(
                event -> List.of(legacyActuatorFlowDefinition()),
                registry,
                new RecordingExecutionLogger());

        runner.run(sensorEvent());

        assertEquals(List.of(NodeType.SENSOR, NodeType.ACTUATOR_CONTROL), executed);
    }

    @Test
    @DisplayName("손상된 Flow에 순환 경로가 있어도 무한 루프 대신 실행 실패로 종료한다")
    void cyclicFlowFailsWithoutInfiniteLoop() {
        List<NodeType> executed = new ArrayList<>();
        NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(
                executor(NodeType.SENSOR, NodeExecutionResult.next("out"), executed),
                executor(NodeType.THRESHOLD, NodeExecutionResult.next("true"), executed)
        ));
        RecordingExecutionLogger logger = new RecordingExecutionLogger();
        FlowRunner runner = new FlowRunner(event -> List.of(cyclicFlowDefinition()), registry, logger);

        runner.run(sensorEvent());

        assertEquals(List.of(NodeType.SENSOR, NodeType.THRESHOLD), executed);
        assertNotNull(logger.failure);
        assertInstanceOf(IllegalStateException.class, logger.failure);
    }

    @Test
    @DisplayName("센서 이벤트 없이 Schedule Trigger부터 Action까지 실행한다")
    void runScheduledFlowWithoutSensorEvent() {
        List<NodeType> executed = new ArrayList<>();
        NodeExecutor scheduleExecutor = new NodeExecutor() {
            @Override
            public NodeType supports() {
                return NodeType.SCHEDULE;
            }

            @Override
            public NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context) {
                assertEquals(ExecutionTriggerType.SCHEDULE, context.triggerType());
                assertEquals(Map.of(), context.metrics());
                executed.add(node.nodeType());
                return NodeExecutionResult.next("out");
            }
        };
        NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(
                scheduleExecutor,
                executor(NodeType.ALERT, NodeExecutionResult.complete(), executed)
        ));
        FlowRunner runner = new FlowRunner(
                event -> List.of(),
                registry,
                new RecordingExecutionLogger()
        );

        runner.runScheduled(
                scheduledFlowDefinition(),
                Instant.parse("2026-08-24T09:00:00Z")
        );

        assertEquals(List.of(NodeType.SCHEDULE, NodeType.ALERT), executed);
    }

    private NodeExecutor executor(
            NodeType nodeType,
            NodeExecutionResult result,
            List<NodeType> executed
    ) {
        return new NodeExecutor() {
            @Override
            public NodeType supports() {
                return nodeType;
            }

            @Override
            public NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context) {
                executed.add(node.nodeType());
                return result;
            }
        };
    }

    private FlowDefinition flowDefinition() {
        return new FlowDefinition(
                1L,
                1L,
                10L,
                "온도 알람",
                null,
                FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-03T00:00:00Z"),
                List.of(
                        node(1L, NodeType.SENSOR),
                        node(2L, NodeType.THRESHOLD),
                        node(3L, NodeType.ALERT)
                ),
                List.of(
                        new LinkDefinition(1L, 1L, 1L, 2L, "out", "in"),
                        new LinkDefinition(2L, 1L, 2L, 3L, "true", "in")
                )
        );
    }

    private FlowDefinition falseBranchFlowDefinition() {
        return new FlowDefinition(
                1L,
                1L,
                10L,
                "온도 알람",
                null,
                FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-03T00:00:00Z"),
                List.of(
                        node(1L, NodeType.SENSOR),
                        node(2L, NodeType.THRESHOLD)
                ),
                List.of(
                        new LinkDefinition(1L, 1L, 1L, 2L, "out", "in")
                )
        );
    }

    private FlowDefinition singleSensorFlowDefinition() {
        return new FlowDefinition(
                2L, 1L, 10L, "잘못된 센서 출력", null, FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-03T00:00:00Z"),
                List.of(node(1L, NodeType.SENSOR)),
                List.of()
        );
    }

    private FlowDefinition legacyActuatorFlowDefinition() {
        return new FlowDefinition(
                3L, 1L, 10L, "기존 액추에이터", null, FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-03T00:00:00Z"),
                List.of(
                        new NodeDefinition(1L, NodeType.SENSOR,
                                JsonNodeFactory.instance.objectNode().put("sensorId", 100L)),
                        new NodeDefinition(2L, NodeType.ACTUATOR_CONTROL,
                                JsonNodeFactory.instance.objectNode().put("deviceId", 900L))
                ),
                List.of(new LinkDefinition(1L, 3L, 1L, 2L, "out", "in"))
        );
    }

    private FlowDefinition cyclicFlowDefinition() {
        return new FlowDefinition(
                4L, 1L, 10L, "순환 경로", null, FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-03T00:00:00Z"),
                List.of(
                        node(1L, NodeType.SENSOR),
                        node(2L, NodeType.THRESHOLD)
                ),
                List.of(
                        new LinkDefinition(1L, 4L, 1L, 2L, "out", "in"),
                        new LinkDefinition(2L, 4L, 2L, 1L, "true", "in")
                )
        );
    }

    private FlowDefinition scheduledFlowDefinition() {
        return new FlowDefinition(
                5L, 1L, 10L, "정기 알림", null, FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-03T00:00:00Z"),
                List.of(
                        new NodeDefinition(1L, NodeType.SCHEDULE,
                                JsonNodeFactory.instance.objectNode().put("cron", "0 0 * * * *")),
                        node(2L, NodeType.ALERT)
                ),
                List.of(new LinkDefinition(1L, 5L, 1L, 2L, "out", "in"))
        );
    }

    private NodeDefinition node(Long nodeId, NodeType nodeType) {
        return new NodeDefinition(nodeId, nodeType, JsonNodeFactory.instance.objectNode());
    }

    private SensorEvent sensorEvent() {
        return new SensorEvent(
                1L,
                10L,
                100L,
                Map.of("temperature", 31.2),
                Instant.parse("2026-08-03T00:00:00Z")
        );
    }

    private static class RecordingExecutionLogger implements ExecutionLogger {

        private Long terminalNodeId;
        private boolean terminalActionReached;
        private RuntimeException failure;

        @Override
        public void eventRouted(SensorEvent event, int flowCount) {
            // This test logger only records terminal execution outcomes.
        }

        @Override
        public void flowStarted(ExecutionLogContext context, Long triggerNodeId) {
            // This test logger only records terminal execution outcomes.
        }

        @Override
        public void flowFinished(
                ExecutionLogContext context,
                Long terminalNodeId,
                boolean terminalActionReached
        ) {
            this.terminalNodeId = terminalNodeId;
            this.terminalActionReached = terminalActionReached;
        }

        @Override
        public void nodeStarted(ExecutionLogContext context, NodeDefinition node) {
            // This test logger only records terminal execution outcomes.
        }

        @Override
        public void nodeFinished(ExecutionLogContext context, NodeDefinition node, NodeExecutionResult result) {
            // This test logger only records terminal execution outcomes.
        }

        @Override
        public void flowFailed(ExecutionLogContext context, RuntimeException exception) {
            this.failure = exception;
        }

        @Override
        public void nodeFailed(ExecutionLogContext context, NodeDefinition node, RuntimeException exception) {
            // This test logger only records terminal execution outcomes.
        }
    }
}
