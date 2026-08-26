package com.nhnacademy.insightonruleengine.runner.execution.executor;

import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import com.nhnacademy.insightonruleengine.runner.execution.location.LocationMetricProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocationNodeExecutor implements NodeExecutor {

    private final NodeParamsParser nodeParamsParser;
    private final LocationMetricProcessor locationMetricProcessor;

    @Override
    public NodeType supports() {
        return NodeType.LOCATION;
    }

    @Override
    public NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context) {
        nodeParamsParser.parse(NodeType.LOCATION, node.configuration());
        locationMetricProcessor.prepare(context);
        return NodeExecutionResult.next("out");
    }
}
