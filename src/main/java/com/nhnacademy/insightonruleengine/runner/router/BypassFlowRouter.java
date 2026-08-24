package com.nhnacademy.insightonruleengine.runner.router;

import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.trigger.SensorParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.recovery.FlowRuntimeRecoveryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** group/location으로 후보를 찾은 뒤 Flow의 Trigger 조건으로 실행. */
@Component
@RequiredArgsConstructor
public class BypassFlowRouter implements FlowRouter {

    private final FlowRuntimeRecoveryService flowRuntimeRecoveryService;
    private final NodeParamsParser nodeParamsParser;

    @Override
    public List<FlowDefinition> route(SensorEvent event) {
        return flowRuntimeRecoveryService.findActiveFlows(event.groupId(), event.locationId())
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
