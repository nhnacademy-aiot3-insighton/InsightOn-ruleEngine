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
import java.util.List;

import com.nhnacademy.insightonruleengine.runner.router.FlowRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlowRunner {

    private final FlowRouter flowRouter;
    private final NodeExecutorRegistry nodeExecutorRegistry;

    public void run(SensorEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event는 필수입니다.");
        }

        List<FlowDefinition> flows = flowRouter.route(event);
        log.info("Prototype rule event received. flowCount={}, deviceId={}, deviceEui={}",
                flows.size(), event.deviceId(), event.deviceEui());
        for (FlowDefinition flow : flows) {
            try {
                runFlow(flow, event);
            } catch (RuntimeException exception) {
                log.warn("Prototype flow execution failed. flowId={}, deviceId={}, deviceEui={}",
                        flow.flowId(), event.deviceId(), event.deviceEui(), exception);
            }
        }
    }

    void runFlow(FlowDefinition flow, SensorEvent event) {
        FlowDefinitionIndex index = new FlowDefinitionIndex(flow);
        NodeDefinition current = findTriggerNode(flow);
        FlowExecutionContext context = new FlowExecutionContext(flow, event);

        log.info("Prototype flow execution started. flowId={}, triggerNodeId={}",
                flow.flowId(), current.nodeId());
        while (current != null) {
            NodeExecutor executor = nodeExecutorRegistry.get(current.nodeType());
            log.info("Prototype node execution started. flowId={}, nodeId={}, nodeType={}",
                    flow.flowId(), current.nodeId(), current.nodeType());

            NodeExecutionResult result = executor.execute(current, context);
            if (result.terminal()) {
                log.info("Prototype flow execution finished. flowId={}, terminalNodeId={}",
                        flow.flowId(), current.nodeId());
                return;
            }

            log.info("Prototype node execution finished. flowId={}, nodeId={}, outputPort={}",
                    flow.flowId(), current.nodeId(), result.outputPort());
            LinkDefinition nextLink = index.requireLink(current.nodeId(), result.outputPort());
            current = index.requireNode(nextLink.targetNodeId());
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
