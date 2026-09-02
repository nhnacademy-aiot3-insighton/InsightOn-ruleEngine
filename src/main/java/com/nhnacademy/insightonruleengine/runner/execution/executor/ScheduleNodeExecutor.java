package com.nhnacademy.insightonruleengine.runner.execution.executor;

import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class ScheduleNodeExecutor implements NodeExecutor {

    @Override
    public NodeType supports() {
        return NodeType.SCHEDULE;
    }

    @Override
    public NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context) {
        return NodeExecutionResult.next("out");
    }
}
