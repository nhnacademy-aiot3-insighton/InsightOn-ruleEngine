package com.nhnacademy.insightonruleengine.runner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.LinkDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.runner.dto.NodeExecutionResult;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.executor.NodeExecutor;
import com.nhnacademy.insightonruleengine.runner.executor.NodeExecutorRegistry;
import com.nhnacademy.insightonruleengine.runner.logging.ExecutionLogger;
import com.nhnacademy.insightonruleengine.runner.router.FlowRouter;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlowRunnerTest {

    @Mock
    private NodeExecutorRegistry nodeExecutorRegistry;

    @Mock
    private ExecutionLogger executionLogger;

    @Mock
    private FlowRouter flowRouter;

    @Mock
    private NodeExecutor sensorExecutor;

    @Mock
    private NodeExecutor thresholdExecutor;

    @Test
    void filterFalseWithoutLinkFinishesNormally() {
        FlowRunner flowRunner = new FlowRunner(flowRouter, nodeExecutorRegistry, executionLogger);
        FlowDefinition flow = definition();
        SensorEvent event = new SensorEvent(1L, 10L, 100L, Map.of("temperature", 20), Instant.now());

        when(nodeExecutorRegistry.get(NodeType.SENSOR)).thenReturn(sensorExecutor);
        when(nodeExecutorRegistry.get(NodeType.THRESHOLD)).thenReturn(thresholdExecutor);
        when(sensorExecutor.execute(any(), any())).thenReturn(NodeExecutionResult.next("out"));
        when(thresholdExecutor.execute(any(), any())).thenReturn(NodeExecutionResult.next("false"));

        flowRunner.runFlow(flow, event);

        verify(executionLogger).flowFinished(
                any(),
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq(false));
    }

    private FlowDefinition definition() {
        return new FlowDefinition(
                1L,
                1L,
                10L,
                "온도 Flow",
                null,
                FlowStatus.ACTIVE,
                OffsetDateTime.now(),
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

    private NodeDefinition node(Long nodeId, NodeType nodeType) {
        return new NodeDefinition(nodeId, nodeType, JsonNodeFactory.instance.objectNode());
    }
}
