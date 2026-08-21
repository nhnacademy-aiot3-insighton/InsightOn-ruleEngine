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
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
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

        List<FlowDefinition> flows;
        try {
            flows = flowRouter.route(event);
        } catch (RuntimeException exception) {
            log.error(
                    "센서 이벤트 라우팅에 실패했습니다. groupId={}, locationId={}, sensorId={}",
                    event.groupId(),
                    event.locationId(),
                    event.sensorId(),
                    exception);
            return;
        }
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
        Set<Long> visitedNodeIds = new HashSet<>();

        executionLogger.flowStarted(logContext, current.nodeId());
        while (current != null) {
            if (!visitedNodeIds.add(current.nodeId())) {
                throw new IllegalStateException(
                        "실행 중 순환 경로를 발견했습니다. flowId=" + flow.flowId()
                                + ", nodeId=" + current.nodeId());
            }
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
            validateExecutionResult(current, result);
            executionLogger.nodeFinished(logContext, current, result);
            if (result.terminal()) {
                executionLogger.flowFinished(logContext, current.nodeId(), true);
                return null;
            }

            LinkDefinition nextLink = index.findLink(current.nodeId(), result.outputPort());
            if (nextLink == null
                    && current.nodeType().getCategory() == NodeType.Category.FILTER
                    && "false".equals(result.outputPort())) {
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

    private void validateExecutionResult(NodeDefinition node, NodeExecutionResult result) {
        if (result == null) {
            throw new IllegalStateException("NodeExecutor가 실행 결과를 반환하지 않았습니다. nodeId=" + node.nodeId());
        }
        boolean action = node.nodeType().getCategory() == NodeType.Category.ACTION;
        if (result.terminal() != action) {
            throw new IllegalStateException(
                    "NodeExecutor 종료 계약이 NodeType과 일치하지 않습니다. nodeId=" + node.nodeId()
                            + ", nodeType=" + node.nodeType());
        }
        if (!result.terminal()
                && !node.nodeType().getPortSchema().outputPorts(null).contains(result.outputPort())) {
            throw new IllegalStateException(
                    "NodeExecutor 출력 포트가 NodeType 계약과 일치하지 않습니다. nodeId=" + node.nodeId()
                            + ", nodeType=" + node.nodeType() + ", outputPort=" + result.outputPort());
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
