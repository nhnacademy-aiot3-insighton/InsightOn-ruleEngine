package com.nhnacademy.insightonruleengine.runner.execution.executor;

import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.AlertParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.application.action.ActionPublisher;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import com.nhnacademy.insightonruleengine.runner.model.action.EngineAlertActionEvent;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AlertNodeExecutor implements NodeExecutor {

    private final NodeParamsParser nodeParamsParser;
    private final ActionPublisher actionPublisher;

    @Override
    public NodeType supports() {
        return NodeType.ALERT;
    }

    // ALERT Action 노드는 도달할 때마다 Action 이벤트를 발행합니다. 반복 억제는 EVENT_GATE가 담당합니다.
    @Override
    public NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context) {
        AlertParams params = nodeParamsParser.parse(NodeType.ALERT, node.configuration());
        Map<String, Object> triggerValue = null;
        if (context.event() != null && context.event().metrics() != null) {
            triggerValue = context.event().metrics();
        }
        EngineAlertActionEvent event = new EngineAlertActionEvent(
                UUID.randomUUID(),
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
