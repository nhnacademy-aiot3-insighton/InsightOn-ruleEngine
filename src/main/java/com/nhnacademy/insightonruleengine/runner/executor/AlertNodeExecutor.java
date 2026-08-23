package com.nhnacademy.insightonruleengine.runner.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.flow.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.AlertParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.action.ActionPublisher;
import com.nhnacademy.insightonruleengine.runner.action.dto.EngineAlertActionEvent;
import com.nhnacademy.insightonruleengine.runner.alert.AlertCountService;
import com.nhnacademy.insightonruleengine.runner.dto.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.dto.NodeExecutionResult;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertNodeExecutor implements NodeExecutor {

    private final NodeParamsParser nodeParamsParser;
    private final AlertCountService alertCountService;
    private final ActionPublisher actionPublisher;
    private final ObjectMapper objectMapper;

    @Override
    public NodeType supports() {
        return NodeType.ALERT;
    }

    //ALERT Action 노드를 실행하며 COUNT 및 Cooldown 통과 시 Action 이벤트를 발행합니다.
    @Override
    public NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context) {
        AlertParams params = nodeParamsParser.parse(NodeType.ALERT, node.configuration());
        if (!alertCountService.shouldPublish(context.flow().flowId(), node.nodeId(), params)) {
            log.info("ALERT action suppressed by COUNT threshold or Cooldown. flowId={}, nodeId={}",
                    context.flow().flowId(), node.nodeId());
            return NodeExecutionResult.complete();
        }
        Map<String, Object> triggerValue = null;
        if (context.event() != null && context.event().metrics() != null) {
            triggerValue = context.event().metrics();
        }
        EngineAlertActionEvent event = new EngineAlertActionEvent(
                context.flow().groupId(),
                context.flow().locationId(),
                context.flow().flowId(),
                params.title(),
                params.message(),
                params.severity(),
                triggerValue
        );
        actionPublisher.publishAlert(event);
        return NodeExecutionResult.complete();
    }
}
