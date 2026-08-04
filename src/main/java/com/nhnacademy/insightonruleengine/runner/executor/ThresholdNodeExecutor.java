package com.nhnacademy.insightonruleengine.runner.executor;

import com.nhnacademy.insightonruleengine.flow.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.ThresholdParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.dto.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.dto.NodeExecutionResult;
import com.nhnacademy.insightonruleengine.runner.evaluator.ThresholdEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ThresholdNodeExecutor implements NodeExecutor {

    private final NodeParamsParser nodeParamsParser;
    private final ThresholdEvaluator thresholdEvaluator;

    @Override
    public NodeType supports() {
        return NodeType.THRESHOLD;
    }

    @Override
    public NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context) {
        ThresholdParams params = nodeParamsParser.parse(NodeType.THRESHOLD, node.configuration());
        boolean matched = thresholdEvaluator.evaluate(params.expression(), context.event());
        return NodeExecutionResult.next(matched ? "true" : "false");
    }
}
