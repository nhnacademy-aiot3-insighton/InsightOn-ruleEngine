package com.nhnacademy.insightonruleengine.runner.executor;

import com.nhnacademy.insightonruleengine.flow.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.node.domain.NodeType;
import com.nhnacademy.insightonruleengine.node.domain.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.dto.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.dto.NodeExecutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SensorNodeExecutor implements NodeExecutor {

    private final NodeParamsParser nodeParamsParser;

    @Override
    public NodeType supports() {
        return NodeType.SENSOR;
    }

    @Override
    public NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context) {
        nodeParamsParser.parse(NodeType.SENSOR, node.configuration());
        return NodeExecutionResult.next("out");
    }
}
