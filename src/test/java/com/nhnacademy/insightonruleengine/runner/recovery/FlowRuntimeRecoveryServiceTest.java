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
import com.nhnacademy.insightonruleengine.runner.redis.InvalidActiveFlowDataException;
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
        when(flowRouteRedisRepository.findFlowIds(GROUP_ID, LOCATION_ID)).thenReturn(Set.of(100L));

        FlowDefinition definition = sampleDefinition(100L, GROUP_ID, LOCATION_ID);
        when(activeFlowRedisRepository.getActiveFlow(GROUP_ID, 100L)).thenReturn(Optional.of(definition));

        List<FlowDefinition> result = recoveryService.findActiveFlows(GROUP_ID, LOCATION_ID);

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).flowId());
    }

    @Test
    @DisplayName("Redis Route MISS 발생 시 위치 전체를 DB 기준으로 재구축합니다")
    void findActiveFlowsRouteMissTest() {
        when(flowRouteRedisRepository.exists(GROUP_ID, LOCATION_ID)).thenReturn(false);

        Flow activeFlow = sampleFlow(100L, GROUP_ID, LOCATION_ID, FlowStatus.ACTIVE);
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(GROUP_ID, LOCATION_ID, FlowStatus.ACTIVE))
                .thenReturn(List.of(activeFlow));

        FlowDefinition definition = sampleDefinition(100L, GROUP_ID, LOCATION_ID);
        when(flowDefinitionAssembler.assembleActive(GROUP_ID, 100L)).thenReturn(definition);

        List<FlowDefinition> result = recoveryService.findActiveFlows(GROUP_ID, LOCATION_ID);

        assertEquals(1, result.size());
        verify(activeFlowRedisRepository).save(definition);
        verify(flowRouteRedisRepository).replace(GROUP_ID, LOCATION_ID, Set.of(100L));
    }

    @Test
    @DisplayName("Redis Route 데이터 손상 시 위치 전체를 DB 기준으로 재구축합니다")
    void findActiveFlowsInvalidDataTest() {
        when(flowRouteRedisRepository.exists(GROUP_ID, LOCATION_ID)).thenReturn(true);
        when(flowRouteRedisRepository.findFlowIds(GROUP_ID, LOCATION_ID))
                .thenThrow(new InvalidActiveFlowDataException("손상된 데이터"));

        Flow activeFlow = sampleFlow(100L, GROUP_ID, LOCATION_ID, FlowStatus.ACTIVE);
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(GROUP_ID, LOCATION_ID, FlowStatus.ACTIVE))
                .thenReturn(List.of(activeFlow));

        FlowDefinition definition = sampleDefinition(100L, GROUP_ID, LOCATION_ID);
        when(flowDefinitionAssembler.assembleActive(GROUP_ID, 100L)).thenReturn(definition);

        List<FlowDefinition> result = recoveryService.findActiveFlows(GROUP_ID, LOCATION_ID);

        assertEquals(1, result.size());
        verify(flowRouteRedisRepository).replace(GROUP_ID, LOCATION_ID, Set.of(100L));
    }

    @Test
    @DisplayName("rebuildAll은 ACTIVE 플로우가 있는 위치뿐만 아니라 비활성 상태만 있는 위치도 빈 Route로 갱신하여 고아 Route를 정리합니다")
    void rebuildAllClearsInactiveLocationRoutesTest() {
        // DB에 그룹 1 위치 10(ACTIVE 플로우 있음)과 그룹 1 위치 20(INACTIVE 플로우만 있음)이 존재
        Flow activeFlow = sampleFlow(100L, GROUP_ID, 10L, FlowStatus.ACTIVE);
        Flow inactiveFlow = sampleFlow(200L, GROUP_ID, 20L, FlowStatus.INACTIVE);

        when(flowRepository.findAll()).thenReturn(List.of(activeFlow, inactiveFlow));

        // 위치 10 조회 결과: ACTIVE 1개
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(GROUP_ID, 10L, FlowStatus.ACTIVE))
                .thenReturn(List.of(activeFlow));
        FlowDefinition definition = sampleDefinition(100L, GROUP_ID, 10L);
        when(flowDefinitionAssembler.assembleActive(GROUP_ID, 100L)).thenReturn(definition);

        // 위치 20 조회 결과: ACTIVE 0개
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(GROUP_ID, 20L, FlowStatus.ACTIVE))
                .thenReturn(List.of());

        int totalRebuilt = recoveryService.rebuildAll();

        assertEquals(1, totalRebuilt);
        // 위치 10은 activeFlow ID로 갱신
        verify(flowRouteRedisRepository).replace(GROUP_ID, 10L, Set.of(100L));
        verify(activeFlowRedisRepository).save(definition);
        // 위치 20은 빈 Set으로 갱신되어 과거 고아 Route 정리
        verify(flowRouteRedisRepository).replace(GROUP_ID, 20L, Set.of());
    }

    private Flow sampleFlow(Long flowId, Long groupId, Long locationId, FlowStatus status) {
        Flow flow = new Flow(groupId, locationId, "Flow " + flowId, "description", status);
        ReflectionTestUtils.setField(flow, "id", flowId);
        return flow;
    }

    private FlowDefinition sampleDefinition(Long flowId, Long groupId, Long locationId) {
        return new FlowDefinition(
                flowId,
                groupId,
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
