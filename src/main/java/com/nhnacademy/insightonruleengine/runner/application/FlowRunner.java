package com.nhnacademy.insightonruleengine.runner.application;

import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinitionIndex;
import com.nhnacademy.insightonruleengine.flow.domain.definition.LinkDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.execution.executor.NodeExecutor;
import com.nhnacademy.insightonruleengine.runner.execution.executor.NodeExecutorRegistry;
import com.nhnacademy.insightonruleengine.runner.observability.ExecutionLogContext;
import com.nhnacademy.insightonruleengine.runner.observability.ExecutionLogger;
import com.nhnacademy.insightonruleengine.runner.application.router.FlowRouter;
import java.time.Instant;
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
            ExecutionLogContext logContext = ExecutionLogContext.telemetry(flow, event);
            runFlow(flow, new FlowExecutionContext(flow, event), logContext);
        }
    }

    void runFlow(FlowDefinition flow, SensorEvent event) {
        runFlow(
                flow,
                new FlowExecutionContext(flow, event),
                ExecutionLogContext.telemetry(flow, event)
        );
    }

    public void runScheduled(FlowDefinition flow, Instant triggeredAt) {
        if (flow == null) {
            throw new IllegalArgumentException("flow는 필수입니다.");
        }
        if (triggeredAt == null) {
            throw new IllegalArgumentException("triggeredAt은 필수입니다.");
        }

        ExecutionLogContext logContext = ExecutionLogContext.scheduled(flow, triggeredAt);
        try {
            NodeDefinition triggerNode = findTriggerNode(flow);
            if (triggerNode.nodeType() != NodeType.SCHEDULE) {
                throw new IllegalArgumentException(
                        "Schedule Trigger Flow가 아닙니다. flowId=" + flow.flowId());
            }
            runFlow(flow, FlowExecutionContext.scheduled(flow, triggeredAt), logContext);
        } catch (RuntimeException exception) {
            executionLogger.flowFailed(logContext, null, exception);
        }
    }

    private void runFlow(
            FlowDefinition flow,
            FlowExecutionContext context,
            ExecutionLogContext logContext
    ) {
        NodeDefinition current = null;
        try {
            FlowDefinitionIndex index = new FlowDefinitionIndex(flow);
            current = findTriggerNode(flow);
            Set<Long> visitedNodeIds = new HashSet<>();

            while (current != null) {
                if (!visitedNodeIds.add(current.nodeId())) {
                    throw new IllegalStateException(
                            "실행 중 순환 경로를 발견했습니다. flowId=" + flow.flowId()
                                    + ", nodeId=" + current.nodeId());
                }
                current = executeNode(index, current, context, logContext);
            }
        } catch (RuntimeException exception) {
            executionLogger.flowFailed(logContext, current, exception);
        }
    }

    private NodeDefinition executeNode(
            FlowDefinitionIndex index,
            NodeDefinition current,
            FlowExecutionContext context,
            ExecutionLogContext logContext
    ) {
        NodeExecutor executor = nodeExecutorRegistry.get(current.nodeType());
        NodeExecutionResult result = executor.execute(current, context);
        validateExecutionResult(current, result);
        if (result.terminal()) {
            executionLogger.flowFinished(logContext, current.nodeId(), true);
            return null;
        }

        LinkDefinition nextLink = index.findLink(current.nodeId(), result.outputPort()).orElse(null);
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
