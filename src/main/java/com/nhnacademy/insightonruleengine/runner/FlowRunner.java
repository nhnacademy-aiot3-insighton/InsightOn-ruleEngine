package com.nhnacademy.insightonruleengine.runner;

import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinitionIndex;
import com.nhnacademy.insightonruleengine.flow.definition.LinkDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.runner.dto.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.dto.NodeExecutionResult;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.executor.NodeExecutor;
import com.nhnacademy.insightonruleengine.runner.executor.NodeExecutorRegistry;
import com.nhnacademy.insightonruleengine.runner.logging.ExecutionLogContext;
import com.nhnacademy.insightonruleengine.runner.logging.ExecutionLogger;
import com.nhnacademy.insightonruleengine.runner.router.FlowRouter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FlowRunner {

    private final FlowRouter flowRouter;
    private final NodeExecutorRegistry nodeExecutorRegistry;
    private final ExecutionLogger executionLogger;

    public void run(SensorEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event는 필수입니다.");
        }

        List<FlowDefinition> flows = flowRouter.route(event);
        executionLogger.eventRouted(event, flows.size());
        for (FlowDefinition flow : flows) {
            ExecutionLogContext logContext = ExecutionLogContext.create(flow, event);
            try {
                runFlow(flow, event, logContext);
            } catch (RuntimeException exception) {
                executionLogger.flowFailed(logContext, exception);
            }
        }
    }

    void runFlow(FlowDefinition flow, SensorEvent event) {
        runFlow(flow, event, ExecutionLogContext.create(flow, event));
    }

    private void runFlow(FlowDefinition flow, SensorEvent event, ExecutionLogContext logContext) {
        FlowDefinitionIndex index = new FlowDefinitionIndex(flow);
        NodeDefinition current = findTriggerNode(flow);
        FlowExecutionContext context = new FlowExecutionContext(flow, event);

        executionLogger.flowStarted(logContext, current.nodeId());
        while (current != null) {
            current = executeNode(index, current, context, logContext);
        }
    }

    private NodeDefinition executeNode(
            FlowDefinitionIndex index,
            NodeDefinition current,
            FlowExecutionContext context,
            ExecutionLogContext logContext
    ) {
        try {
            NodeExecutor executor = nodeExecutorRegistry.get(current.nodeType());
            executionLogger.nodeStarted(logContext, current);

            NodeExecutionResult result = executor.execute(current, context);
            executionLogger.nodeFinished(logContext, current, result);
            if (result.terminal()) {
                executionLogger.flowFinished(logContext, current.nodeId(), true);
                return null;
            }

            LinkDefinition nextLink = index.findLink(current.nodeId(), result.outputPort());
            if (nextLink == null && "false".equals(result.outputPort())) {
                executionLogger.flowFinished(logContext, current.nodeId(), false);
                return null;
            }
            if (nextLink == null) {
                nextLink = index.requireLink(current.nodeId(), result.outputPort());
            }
            return index.requireNode(nextLink.targetNodeId());
        } catch (RuntimeException exception) {
            executionLogger.nodeFailed(logContext, current, exception);
            throw exception;
        }
    }

    private NodeDefinition findTriggerNode(FlowDefinition flow) {
        return flow.nodes()
                .stream()
                .filter(node -> node.nodeType().getCategory() == NodeType.Category.TRIGGER)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Trigger Node가 없습니다. flowId=" + flow.flowId()));
    }
}
