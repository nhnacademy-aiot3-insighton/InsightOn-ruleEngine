package com.nhnacademy.insightonruleengine.runner.router;

import com.nhnacademy.insightonruleengine.flow.cache.ActiveFlowDefinitionProvider;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.trigger.SensorParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 장소 단위로 캐시된 ACTIVE Flow를 가져온 뒤 Trigger Node의 종류에 따라 라우팅합니다.
 * LOCATION Flow는 장소의 모든 센서 이벤트를 받고, SENSOR Flow는 설정된 센서 이벤트만 받습니다.
 */
@Component
@RequiredArgsConstructor
public class BypassFlowRouter implements FlowRouter {

    private final ActiveFlowDefinitionProvider activeFlowDefinitionProvider;
    private final NodeParamsParser nodeParamsParser;

    @Override
    public List<FlowDefinition> route(SensorEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event는 필수입니다.");
        }
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
