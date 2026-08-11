package com.nhnacademy.insightonruleengine.runner.router;

import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로토타입 Engine을 빠르게 개발하기 위한 실제 라우팅 로직이 빠진 임시 라우터
 */
@Component
@RequiredArgsConstructor
public class BypassFlowRouter implements FlowRouter {

    private final FlowRepository flowRepository;
    private final FlowDefinitionAssembler flowDefinitionAssembler;

    @Override
    @Transactional(readOnly = true)
    public List<FlowDefinition> route(SensorEvent event) {
        return flowRepository.findAllByGroupIdAndLocationIdAndStatus(
                        event.groupId(),
                        event.locationId(),
                        FlowStatus.ACTIVE)
                .stream()
                .map(flow -> flowDefinitionAssembler.assemble(flow.getGroupId(), flow.getId()))
                .toList();
    }
}
