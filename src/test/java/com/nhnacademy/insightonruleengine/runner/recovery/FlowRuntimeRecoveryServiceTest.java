package com.nhnacademy.insightonruleengine.runner.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import com.nhnacademy.insightonruleengine.runner.redis.ActiveFlowRedisRepository;
import com.nhnacademy.insightonruleengine.runner.redis.FlowRouteRedisRepository;
import com.nhnacademy.insightonruleengine.runner.redis.InvalidRouteDataException;
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
class FlowRuntimeRecoveryServiceTest {

    private static final long GROUP_ID = 1L;
    private static final long LOCATION_ID = 10L;
    private static final long FLOW_ID = 100L;

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private FlowDefinitionAssembler flowDefinitionAssembler;

    @Mock
    private FlowRouteRedisRepository flowRouteRedisRepository;

    @Mock
    private ActiveFlowRedisRepository activeFlowRedisRepository;

    @InjectMocks
    private FlowRuntimeRecoveryService recoveryService;

    @Test
    @DisplayName("Redis Route에 존재하는 Flow들의 정의를 조회하여 반환합니다")
    void findActiveFlowsFromRedisTest() {
        when(flowRouteRedisRepository.exists(GROUP_ID, LOCATION_ID)).thenReturn(true);
        when(flowRouteRedisRepository.findFlowIds(GROUP_ID, LOCATION_ID)).thenReturn(Set.of(FLOW_ID));

        FlowDefinition definition = sampleDefinition(LOCATION_ID);
        when(activeFlowRedisRepository.getActiveFlow(GROUP_ID, FLOW_ID)).thenReturn(Optional.of(definition));

        List<FlowDefinition> result = recoveryService.findActiveFlows(GROUP_ID, LOCATION_ID);

        assertEquals(1, result.size());
        assertEquals(FLOW_ID, result.getFirst().flowId());
    }

    @Test
    @DisplayName("Redis Route MISS 발생 시 위치 전체를 DB 기준으로 재구축합니다")
    void findActiveFlowsRouteMissTest() {
        when(flowRouteRedisRepository.exists(GROUP_ID, LOCATION_ID)).thenReturn(false);

        Flow activeFlow = sampleFlow(FLOW_ID, LOCATION_ID, FlowStatus.ACTIVE);
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(GROUP_ID, LOCATION_ID, FlowStatus.ACTIVE))
                .thenReturn(List.of(activeFlow));

        FlowDefinition definition = sampleDefinition(LOCATION_ID);
        when(flowDefinitionAssembler.assembleActive(GROUP_ID, FLOW_ID)).thenReturn(definition);

        List<FlowDefinition> result = recoveryService.findActiveFlows(GROUP_ID, LOCATION_ID);

        assertEquals(1, result.size());
        verify(activeFlowRedisRepository).save(definition);
        verify(flowRouteRedisRepository).replace(GROUP_ID, LOCATION_ID, Set.of(FLOW_ID));
    }

    @Test
    @DisplayName("Redis Route 데이터 손상 시 위치 전체를 DB 기준으로 재구축합니다")
    void findActiveFlowsInvalidDataTest() {
        when(flowRouteRedisRepository.exists(GROUP_ID, LOCATION_ID)).thenReturn(true);
        when(flowRouteRedisRepository.findFlowIds(GROUP_ID, LOCATION_ID))
                .thenThrow(new InvalidRouteDataException("손상된 데이터", new NumberFormatException()));

        Flow activeFlow = sampleFlow(FLOW_ID, LOCATION_ID, FlowStatus.ACTIVE);
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(GROUP_ID, LOCATION_ID, FlowStatus.ACTIVE))
                .thenReturn(List.of(activeFlow));

        FlowDefinition definition = sampleDefinition(LOCATION_ID);
        when(flowDefinitionAssembler.assembleActive(GROUP_ID, FLOW_ID)).thenReturn(definition);

        List<FlowDefinition> result = recoveryService.findActiveFlows(GROUP_ID, LOCATION_ID);

        assertEquals(1, result.size());
        verify(flowRouteRedisRepository).replace(GROUP_ID, LOCATION_ID, Set.of(FLOW_ID));
    }

    @Test
    @DisplayName("rebuildAll은 ACTIVE 플로우가 있는 위치뿐만 아니라 비활성 상태만 있는 위치도 빈 Route로 갱신하여 고아 Route를 정리합니다")
    void rebuildAllClearsInactiveLocationRoutesTest() {
        // DB에 그룹 1 위치 10(ACTIVE 플로우 있음)과 그룹 1 위치 20(INACTIVE 플로우만 있음)이 존재
        Flow activeFlow = sampleFlow(FLOW_ID, 10L, FlowStatus.ACTIVE);
        Flow inactiveFlow = sampleFlow(200L, 20L, FlowStatus.INACTIVE);

        when(flowRepository.findAll()).thenReturn(List.of(activeFlow, inactiveFlow));

        // 위치 10 조회 결과: ACTIVE 1개
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(GROUP_ID, 10L, FlowStatus.ACTIVE))
                .thenReturn(List.of(activeFlow));
        FlowDefinition definition = sampleDefinition(10L);
        when(flowDefinitionAssembler.assembleActive(GROUP_ID, FLOW_ID)).thenReturn(definition);

        // 위치 20 조회 결과: ACTIVE 0개
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(GROUP_ID, 20L, FlowStatus.ACTIVE))
                .thenReturn(List.of());

        int totalRebuilt = recoveryService.rebuildAll();

        assertEquals(1, totalRebuilt);
        // 위치 10은 activeFlow ID로 갱신
        verify(flowRouteRedisRepository).replace(GROUP_ID, 10L, Set.of(FLOW_ID));
        verify(activeFlowRedisRepository).save(definition);
        // 위치 20은 빈 Set으로 갱신되어 과거 고아 Route 정리
        verify(flowRouteRedisRepository).replace(GROUP_ID, 20L, Set.of());
    }

    private Flow sampleFlow(Long flowId, Long locationId, FlowStatus status) {
        Flow flow = new Flow(GROUP_ID, locationId, "Flow " + flowId, "description", status);
        ReflectionTestUtils.setField(flow, "id", flowId);
        return flow;
    }

    private FlowDefinition sampleDefinition(Long locationId) {
        return new FlowDefinition(
                FLOW_ID,
                GROUP_ID,
                locationId,
                "Test Flow",
                "Description",
                FlowStatus.ACTIVE,
                OffsetDateTime.now(),
                List.of(),
                List.of()
        );
    }
}
