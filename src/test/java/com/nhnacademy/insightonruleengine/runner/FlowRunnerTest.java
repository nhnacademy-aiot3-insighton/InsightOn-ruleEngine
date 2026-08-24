package com.nhnacademy.insightonruleengine.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.LinkDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.runner.dto.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.dto.NodeExecutionResult;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.executor.NodeExecutor;
import com.nhnacademy.insightonruleengine.runner.executor.NodeExecutorRegistry;
import com.nhnacademy.insightonruleengine.runner.logging.ExecutionLogContext;
import com.nhnacademy.insightonruleengine.runner.logging.ExecutionLogger;
import com.nhnacademy.insightonruleengine.runner.router.FlowRouter;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    @DisplayName("한 Flow 실행이 실패해도 다음 Flow를 계속 실행한다")
    void isolateFlowFailureTest() {
        List<Long> attemptedFlowIds = new ArrayList<>();
        FlowRouter router = event -> List.of(singleNodeFlowDefinition(1L), singleNodeFlowDefinition(2L));
        NodeExecutor sensorExecutor = new NodeExecutor() {
            @Override
            public NodeType supports() {
                return NodeType.SENSOR;
            }

            @Override
            public NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context) {
                Long flowId = context.flow().flowId();
                attemptedFlowIds.add(flowId);
                if (flowId == 1L) {
                    throw new IllegalStateException("첫 Flow 실행 실패");
                }
                return NodeExecutionResult.complete();
            }
        };
        RecordingExecutionLogger logger = new RecordingExecutionLogger();
        FlowRunner runner = new FlowRunner(
                router,
                new NodeExecutorRegistry(List.of(sensorExecutor)),
                logger
        );

        runner.run(sensorEvent());

        assertEquals(List.of(1L, 2L), attemptedFlowIds);
        assertEquals(List.of(1L), logger.failedFlowIds);
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

    private FlowDefinition singleNodeFlowDefinition(Long flowId) {
        return new FlowDefinition(
                flowId,
                1L,
                10L,
                "Flow " + flowId,
                null,
                FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-03T00:00:00Z"),
                List.of(node(flowId, NodeType.SENSOR)),
                List.of()
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
        private final List<Long> failedFlowIds = new ArrayList<>();

        @Override
        public void eventRouted(SensorEvent event, int flowCount) {
        }

        @Override
        public void flowStarted(ExecutionLogContext context, Long triggerNodeId) {
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
        }

        @Override
        public void nodeFinished(ExecutionLogContext context, NodeDefinition node, NodeExecutionResult result) {
        }

        @Override
        public void flowFailed(ExecutionLogContext context, RuntimeException exception) {
            failedFlowIds.add(context.flowId());
        }

        @Override
        public void nodeFailed(ExecutionLogContext context, NodeDefinition node, RuntimeException exception) {
        }
    }
}
