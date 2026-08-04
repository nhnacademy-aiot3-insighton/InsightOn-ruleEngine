package com.nhnacademy.insightonruleengine.runner.executor;

import com.nhnacademy.insightonruleengine.flow.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.node.domain.NodeType;
import com.nhnacademy.insightonruleengine.runner.dto.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.dto.NodeExecutionResult;

public interface NodeExecutor {
    NodeType supports();
    NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context);
}
