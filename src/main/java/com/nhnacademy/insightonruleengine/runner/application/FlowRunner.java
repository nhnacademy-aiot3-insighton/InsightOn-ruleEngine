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
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

        List<FlowDefinition> flows;
        try {
            flows = flowRouter.route(event);
        } catch (RuntimeException exception) {
            executionLogger.routingFailed(event, exception);
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
            ArrayDeque<NodeDefinition> pendingActions = new ArrayDeque<>();

            while (current != null) {
                requireNotVisited(flow, visitedNodeIds, current);
                NodeExecutionResult result = executeNode(current, context);
                if (result.terminal()) {
                    if (pendingActions.isEmpty()) {
                        executionLogger.flowFinished(logContext, current.nodeId(), true);
                        return;
                    }
                    current = pendingActions.removeFirst();
                    continue;
                }

                List<LinkDefinition> nextLinks = resolveNextLinks(index, current, result);
                if (nextLinks == null) {
                    executionLogger.flowFinished(logContext, current.nodeId(), false);
                    return;
                }

                List<NodeDefinition> nextNodes = nextLinks.stream()
                        .map(link -> index.requireNode(link.targetNodeId()))
                        .toList();
                validateFanOutTargets(current, result.outputPort(), nextNodes);
                current = nextNodes.getFirst();
                if (nextNodes.size() > 1) {
                    pendingActions.addAll(nextNodes.subList(1, nextNodes.size()));
                }
            }
        } catch (RuntimeException exception) {
            executionLogger.flowFailed(logContext, current, exception);
        }
    }

    // 실행 중 같은 Node를 다시 방문하면 순환 경로이므로 즉시 실패시킵니다.
    private void requireNotVisited(FlowDefinition flow, Set<Long> visitedNodeIds, NodeDefinition current) {
        if (!visitedNodeIds.add(current.nodeId())) {
            throw new IllegalStateException(
                    "실행 중 순환 경로를 발견했습니다. flowId=" + flow.flowId()
                            + ", nodeId=" + current.nodeId());
        }
    }

    // Filter가 false로 끝나 연결된 Link가 없으면 정상 종료를 뜻하는 null을 반환합니다.
    private List<LinkDefinition> resolveNextLinks(
            FlowDefinitionIndex index,
            NodeDefinition current,
            NodeExecutionResult result) {
        List<LinkDefinition> nextLinks = index.findLinks(current.nodeId(), result.outputPort());
        if (!nextLinks.isEmpty()) {
            return nextLinks;
        }
        if (current.nodeType().getCategory() == NodeType.Category.FILTER
                && "false".equals(result.outputPort())) {
            return null;
        }
        return index.requireLinks(current.nodeId(), result.outputPort());
    }

    private NodeExecutionResult executeNode(
            NodeDefinition current,
            FlowExecutionContext context
    ) {
        NodeExecutor executor = nodeExecutorRegistry.get(current.nodeType());
        NodeExecutionResult result = executor.execute(current, context);
        validateExecutionResult(current, result);
        return result;
    }

    private void validateFanOutTargets(
            NodeDefinition source,
            String sourcePort,
            List<NodeDefinition> targets
    ) {
        if (targets.size() <= 1) {
            return;
        }
        boolean actionTargetsOnly = targets.stream()
                .allMatch(target -> target.nodeType().getCategory() == NodeType.Category.ACTION);
        if (!actionTargetsOnly) {
            throw new IllegalStateException(
                    "Action이 아닌 Node로의 fan-out은 지원하지 않습니다. sourceNodeId="
                            + source.nodeId() + ", sourcePort=" + sourcePort
            );
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
