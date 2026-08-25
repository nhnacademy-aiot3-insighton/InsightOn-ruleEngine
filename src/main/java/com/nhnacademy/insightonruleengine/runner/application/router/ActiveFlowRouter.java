package com.nhnacademy.insightonruleengine.runner.application.router;

import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.trigger.SensorParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.infrastructure.cache.ActiveFlowDefinitionProvider;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** group/location의 활성 Flow 중 Telemetry trigger와 일치하는 실행 대상을 선택한다. */
@Component
@RequiredArgsConstructor
public class ActiveFlowRouter implements FlowRouter {

    private final ActiveFlowDefinitionProvider activeFlowDefinitionProvider;
    private final NodeParamsParser nodeParamsParser;

    @Override
    public List<FlowDefinition> route(SensorEvent event) {
        return activeFlowDefinitionProvider.find(event.groupId(), event.locationId())
                .stream()
                .filter(flow -> matchesTrigger(flow, event))
                .toList();
    }

    private boolean matchesTrigger(FlowDefinition flow, SensorEvent event) {
        NodeDefinition trigger = flow.nodes().stream()
                .filter(node -> node.nodeType().getCategory() == NodeType.Category.TRIGGER)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Trigger Node가 없습니다. flowId=" + flow.flowId()));

        if (trigger.nodeType() == NodeType.LOCATION) {
            return true;
        }
        if (trigger.nodeType() == NodeType.SENSOR) {
            SensorParams params = nodeParamsParser.parse(NodeType.SENSOR, trigger.configuration());
            return params.sensorId().equals(event.sensorId());
        }
        return false;
    }
}
