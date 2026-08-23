package com.nhnacademy.insightonruleengine.runner.execution.executor;

import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.runner.dto.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.dto.NodeExecutionResult;

public interface NodeExecutor {
    NodeType supports();
    NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context);
}
