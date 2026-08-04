package com.nhnacademy.insightonruleengine.runner.executor;

import com.nhnacademy.insightonruleengine.flow.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.node.domain.NodeType;
import com.nhnacademy.insightonruleengine.node.domain.params.action.AlertParams;
import com.nhnacademy.insightonruleengine.node.domain.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.dto.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.dto.NodeExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertNodeExecutor implements NodeExecutor {

    private final NodeParamsParser nodeParamsParser;

    @Override
    public NodeType supports() {
        return NodeType.ALERT;
    }

    @Override
    public NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context) {
        AlertParams params = nodeParamsParser.parse(NodeType.ALERT, node.configuration());
        log.info(
                "Prototype alert executed. flowId={}, nodeId={}, severity={}, message={}",
                context.flow().flowId(),
                node.nodeId(),
                params.severity(),
                params.message()
        );
        return NodeExecutionResult.complete();
    }
}
