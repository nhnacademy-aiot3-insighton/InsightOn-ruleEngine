package com.nhnacademy.insightonruleengine.runner.execution.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.ThresholdParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.execution.evaluator.ThresholdEvaluator;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThresholdNodeExecutorTest {

    private final NodeParamsParser nodeParamsParser = mock(NodeParamsParser.class);
    private final ThresholdNodeExecutor executor = new ThresholdNodeExecutor(
            nodeParamsParser,
            new ThresholdEvaluator()
    );
    private final NodeDefinition node = new NodeDefinition(
            10L,
            NodeType.THRESHOLD,
            JsonNodeFactory.instance.objectNode()
    );
    private final FlowExecutionContext context = mock(FlowExecutionContext.class);

    @Test
    void missingMetricUsesFalsePort() {
        when(nodeParamsParser.<ThresholdParams>parse(eq(NodeType.THRESHOLD), any()))
                .thenReturn(new ThresholdParams("#metrics['humidity'] != 50"));
        when(context.metrics()).thenReturn(Map.of("temperature", 31.2));

        assertEquals("false", executor.execute(node, context).outputPort());
    }

    @Test
    void validNumericMetricUsesTruePort() {
        when(nodeParamsParser.<ThresholdParams>parse(eq(NodeType.THRESHOLD), any()))
                .thenReturn(new ThresholdParams("#metrics['temperature'] > 30"));
        when(context.metrics()).thenReturn(Map.of("temperature", 31.2));

        assertEquals("true", executor.execute(node, context).outputPort());
    }
}
