package com.nhnacademy.insightonruleengine.runner.application.lifecycle;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.runner.application.lifecycle.FlowRuntimeSynchronizer;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.application.assembly.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.event.FlowRuntimeChangeEvent;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.AlertCountRedisRepository;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.ActiveFlowRedisRepository;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.FlowRouteRedisRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FlowRuntimeSynchronizerTest {

    private static final long GROUP_ID = 1L;
    private static final long LOCATION_ID = 2L;
    private static final long FLOW_ID = 10L;

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private FlowDefinitionAssembler flowDefinitionAssembler;

    @Mock
    private FlowRouteRedisRepository flowRouteRedisRepository;

    @Mock
    private ActiveFlowRedisRepository activeFlowRedisRepository;

    @Mock
    private AlertCountRedisRepository alertCountRedisRepository;

    @InjectMocks
    private FlowRuntimeSynchronizer synchronizer;

    @Test
    @DisplayName("활성화 시 FlowDefinition을 조립하여 Redis에 저장하고 Route를 갱신합니다")
    void activateFlowTest() {
        FlowRuntimeChangeEvent event = FlowRuntimeChangeEvent.activate(GROUP_ID, LOCATION_ID, FLOW_ID);
        FlowDefinition definition = sampleDefinition();
        Flow activeFlow = sampleFlow(FLOW_ID, FlowStatus.ACTIVE);

        when(flowDefinitionAssembler.assemble(GROUP_ID, FLOW_ID)).thenReturn(definition);
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(GROUP_ID, LOCATION_ID, FlowStatus.ACTIVE))
                .thenReturn(List.of(activeFlow));

        synchronizer.activate(event);

        verify(flowDefinitionAssembler).assemble(GROUP_ID, FLOW_ID);
        verify(activeFlowRedisRepository).save(definition);
        verify(flowRouteRedisRepository).replace(GROUP_ID, LOCATION_ID, Set.of(FLOW_ID));
    }

    @Test
    @DisplayName("비활성화 상태인 Flow에 대한 제거 이벤트는 Redis Definition과 Alert State를 정상 삭제합니다")
    void removeInactiveFlowTest() {
        FlowRuntimeChangeEvent event = FlowRuntimeChangeEvent.remove(GROUP_ID, LOCATION_ID, FLOW_ID, Set.of(100L));
        Flow inactiveFlow = sampleFlow(FLOW_ID, FlowStatus.INACTIVE);

        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(GROUP_ID, LOCATION_ID, FlowStatus.ACTIVE))
                .thenReturn(List.of());
        when(flowRepository.findById(FLOW_ID)).thenReturn(Optional.of(inactiveFlow));

        synchronizer.remove(event);

        verify(flowRouteRedisRepository).replace(GROUP_ID, LOCATION_ID, Set.of());
        verify(activeFlowRedisRepository).delete(GROUP_ID, FLOW_ID);
        verify(alertCountRedisRepository).deleteStates(FLOW_ID, Set.of(100L));
    }

    @Test
    @DisplayName("DB에서 이미 삭제된 Flow에 대한 제거 이벤트는 Redis Definition과 Alert State를 정상 삭제합니다")
    void removeDeletedFlowTest() {
        FlowRuntimeChangeEvent event = FlowRuntimeChangeEvent.remove(GROUP_ID, LOCATION_ID, FLOW_ID, Set.of(100L));

        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(GROUP_ID, LOCATION_ID, FlowStatus.ACTIVE))
                .thenReturn(List.of());
        when(flowRepository.findById(FLOW_ID)).thenReturn(Optional.empty());

        synchronizer.remove(event);

        verify(flowRouteRedisRepository).replace(GROUP_ID, LOCATION_ID, Set.of());
        verify(activeFlowRedisRepository).delete(GROUP_ID, FLOW_ID);
        verify(alertCountRedisRepository).deleteStates(FLOW_ID, Set.of(100L));
    }

    @Test
    @DisplayName("현재 DB에서 ACTIVE 상태인 Flow에 대한 지연된 제거 이벤트는 Definition 및 Alert State 삭제를 무시합니다")
    void ignoreStaleRemoveEventWhenFlowIsActiveTest() {
        FlowRuntimeChangeEvent event = FlowRuntimeChangeEvent.remove(GROUP_ID, LOCATION_ID, FLOW_ID, Set.of(100L));
        Flow activeFlow = sampleFlow(FLOW_ID, FlowStatus.ACTIVE);

        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(GROUP_ID, LOCATION_ID, FlowStatus.ACTIVE))
                .thenReturn(List.of(activeFlow));
        when(flowRepository.findById(FLOW_ID)).thenReturn(Optional.of(activeFlow));

        synchronizer.remove(event);

        verify(flowRouteRedisRepository).replace(GROUP_ID, LOCATION_ID, Set.of(FLOW_ID));
        verify(activeFlowRedisRepository, never()).delete(GROUP_ID, FLOW_ID);
        verify(alertCountRedisRepository, never()).deleteStates(FLOW_ID, Set.of(100L));
    }

    private Flow sampleFlow(Long flowId, FlowStatus status) {
        Flow flow = new Flow(GROUP_ID, LOCATION_ID, "Flow " + flowId, "description", status);
        ReflectionTestUtils.setField(flow, "id", flowId);
        return flow;
    }

    private FlowDefinition sampleDefinition() {
        return new FlowDefinition(
                FLOW_ID,
                GROUP_ID,
                LOCATION_ID,
                "Test Flow",
                "Description",
                FlowStatus.ACTIVE,
                OffsetDateTime.now(),
                List.of(),
                List.of()
        );
    }
}
