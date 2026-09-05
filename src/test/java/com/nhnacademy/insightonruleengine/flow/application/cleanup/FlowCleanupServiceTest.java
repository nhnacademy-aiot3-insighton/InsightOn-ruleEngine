package com.nhnacademy.insightonruleengine.flow.application.cleanup;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.NodeRepository;
import com.nhnacademy.insightonruleengine.runner.application.schedule.ScheduleFlowScheduler;
import com.nhnacademy.insightonruleengine.runner.infrastructure.cache.ActiveFlowDefinitionProvider;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.EventGateStateRedisRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FlowCleanupServiceTest {

    @Mock
    private FlowRepository flowRepository;
    @Mock
    private NodeRepository nodeRepository;
    @Mock
    private ActiveFlowDefinitionProvider activeFlowDefinitionProvider;
    @Mock
    private EventGateStateRedisRepository eventGateStateRedisRepository;
    @Mock
    private FlowCleanupDBService databaseCleanupService;
    @Mock
    private ScheduleFlowScheduler scheduleFlowScheduler;

    private FlowCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = new FlowCleanupService(
                flowRepository,
                nodeRepository,
                activeFlowDefinitionProvider,
                eventGateStateRedisRepository,
                databaseCleanupService,
                scheduleFlowScheduler
        );
    }

    @Test
    @DisplayName("그룹 DB 삭제 전·후에 현재 실행 캐시와 상태를 정리합니다")
    void redisThenDatabaseCleanupTest() {
        Flow first = flow(100L, 10L, FlowStatus.ACTIVE);
        Flow second = flow(200L, 10L, FlowStatus.INACTIVE);
        Flow third = flow(300L, 20L, FlowStatus.ARCHIVED);
        Node eventGate = eventGateNode(1004L);
        Node nonAlert = thresholdNode();
        when(flowRepository.findAllByGroupId(1L)).thenReturn(List.of(third, first, second));
        when(nodeRepository.findByFlowIdIn(List.of(100L, 200L, 300L)))
                .thenReturn(List.of(eventGate, nonAlert));
        doAnswer(invocation -> {
            verify(scheduleFlowScheduler).cancelAll(List.of(100L, 200L, 300L));
            verify(activeFlowDefinitionProvider).evictNow(1L, 10L);
            verify(activeFlowDefinitionProvider).evictNow(1L, 20L);
            verify(activeFlowDefinitionProvider).evictNow(1L, 30L);
            return null;
        }).when(databaseCleanupService).deleteByGroupId(1L);

        cleanupService.cleanupByGroup(1L, List.of(10L, 20L, 30L));

        verify(activeFlowDefinitionProvider, times(2)).evictNow(1L, 10L);
        verify(activeFlowDefinitionProvider, times(2)).evictNow(1L, 20L);
        verify(activeFlowDefinitionProvider, times(2)).evictNow(1L, 30L);
        verify(eventGateStateRedisRepository, times(2)).deleteStates(100L, Set.of(1004L));
        verify(eventGateStateRedisRepository, times(2)).deleteStates(200L, Set.of());
        verify(eventGateStateRedisRepository, times(2)).deleteStates(300L, Set.of());

        verify(databaseCleanupService).deleteByGroupId(1L);
        verify(scheduleFlowScheduler).cancelAll(List.of(100L, 200L, 300L));
    }

    @Test
    @DisplayName("장소 DB 삭제 전·후에 조회한 Flow의 실행 캐시와 상태를 정리합니다")
    void locationCleanupTest() {
        Flow first = flow(100L, 1L, 10L, FlowStatus.ACTIVE);
        Flow second = flow(200L, 2L, 10L, FlowStatus.ARCHIVED);
        when(flowRepository.findAllByLocationId(10L)).thenReturn(List.of(second, first));
        when(nodeRepository.findByFlowIdIn(List.of(100L, 200L))).thenReturn(List.of());
        doAnswer(invocation -> {
            verify(scheduleFlowScheduler).cancelAll(List.of(100L, 200L));
            verify(activeFlowDefinitionProvider).evictNow(1L, 10L);
            verify(activeFlowDefinitionProvider).evictNow(2L, 10L);
            return null;
        }).when(databaseCleanupService).deleteByLocationId(10L);

        cleanupService.cleanupByLocation(10L);

        verify(activeFlowDefinitionProvider, times(2)).evictNow(1L, 10L);
        verify(activeFlowDefinitionProvider, times(2)).evictNow(2L, 10L);
        verify(eventGateStateRedisRepository, times(2)).deleteStates(100L, Set.of());
        verify(eventGateStateRedisRepository, times(2)).deleteStates(200L, Set.of());
        verify(databaseCleanupService).deleteByLocationId(10L);
        verify(scheduleFlowScheduler).cancelAll(List.of(100L, 200L));
    }

    @Test
    @DisplayName("Redis 정리에 실패하면 DB 삭제를 시작하지 않고 예외를 전파합니다")
    void redisFailurePreventsDatabaseCleanupTest() {
        Flow flow = flow(100L, 10L, FlowStatus.ACTIVE);
        when(flowRepository.findAllByGroupId(1L)).thenReturn(List.of(flow));
        when(nodeRepository.findByFlowIdIn(List.of(100L))).thenReturn(List.of());
        doThrow(new RedisConnectionFailureException("Redis unavailable"))
                .when(activeFlowDefinitionProvider).evictNow(1L, 10L);
        List<Long> locationIds = List.of(10L);

        assertThrows(
                RedisConnectionFailureException.class,
                () -> cleanupService.cleanupByGroup(1L, locationIds)
        );

        verify(databaseCleanupService, never()).deleteByGroupId(1L);
    }

    @Test
    @DisplayName("이미 삭제되어 대상 Flow가 없어도 DB 정리를 멱등하게 호출합니다")
    void absentGroupIsIdempotentTest() {
        when(flowRepository.findAllByGroupId(1L)).thenReturn(List.of());

        cleanupService.cleanupByGroup(1L, List.of(10L));
        cleanupService.cleanupByGroup(1L, List.of(10L));

        verify(nodeRepository, never()).findByFlowIdIn(org.mockito.ArgumentMatchers.anyList());
        verify(activeFlowDefinitionProvider, times(4)).evictNow(1L, 10L);
        verify(databaseCleanupService, times(2)).deleteByGroupId(1L);
    }

    @Test
    @DisplayName("Redis 정리 후 DB가 실패해도 재시도하면 같은 정리를 반복하고 완료합니다")
    void databaseFailureCanBeRetriedTest() {
        Flow flow = flow(100L, 10L, FlowStatus.ACTIVE);
        when(flowRepository.findAllByGroupId(1L)).thenReturn(List.of(flow));
        when(nodeRepository.findByFlowIdIn(List.of(100L))).thenReturn(List.of());
        doThrow(new IllegalStateException("DB unavailable"))
                .doNothing()
                .when(databaseCleanupService).deleteByGroupId(1L);
        List<Long> locationIds = List.of(10L);

        assertThrows(
                IllegalStateException.class,
                () -> cleanupService.cleanupByGroup(1L, locationIds)
        );
        cleanupService.cleanupByGroup(1L, List.of(10L));

        verify(activeFlowDefinitionProvider, times(3)).evictNow(1L, 10L);
        verify(eventGateStateRedisRepository, times(3)).deleteStates(100L, Set.of());
        verify(databaseCleanupService, times(2)).deleteByGroupId(1L);
        verify(scheduleFlowScheduler, times(2)).cancelAll(List.of(100L));
    }

    @Test
    @DisplayName("이미 삭제된 Location 이벤트도 DB 정리를 멱등하게 완료합니다")
    void absentLocationIsIdempotentTest() {
        when(flowRepository.findAllByLocationId(10L)).thenReturn(List.of());

        cleanupService.cleanupByLocation(10L);
        cleanupService.cleanupByLocation(10L);

        verify(nodeRepository, never()).findByFlowIdIn(org.mockito.ArgumentMatchers.anyList());
        verify(databaseCleanupService, times(2)).deleteByLocationId(10L);
    }

    @Test
    @DisplayName("그룹 삭제는 양수가 아닌 그룹 ID와 잘못된 장소 목록을 거부합니다")
    void invalidGroupDeletionIdsTest() {
        List<Long> emptyLocationIds = List.of();
        List<Long> zeroLocationId = List.of(0L);
        List<Long> negativeLocationId = List.of(-1L);

        assertThrows(IllegalArgumentException.class, () -> cleanupService.cleanupByGroup(null, emptyLocationIds));
        assertThrows(IllegalArgumentException.class, () -> cleanupService.cleanupByGroup(0L, emptyLocationIds));
        assertThrows(IllegalArgumentException.class, () -> cleanupService.cleanupByGroup(1L, null));
        assertThrows(IllegalArgumentException.class, () -> cleanupService.cleanupByGroup(1L, zeroLocationId));
        assertThrows(IllegalArgumentException.class, () -> cleanupService.cleanupByGroup(1L, negativeLocationId));

        List<Long> locationIdsWithNull = new ArrayList<>();
        locationIdsWithNull.add(null);
        assertThrows(
                IllegalArgumentException.class,
                () -> cleanupService.cleanupByGroup(1L, locationIdsWithNull)
        );
    }

    @Test
    @DisplayName("장소 삭제는 양수인 Location ID만 허용합니다")
    void invalidLocationDeletionIdTest() {
        assertThrows(IllegalArgumentException.class, () -> cleanupService.cleanupByLocation(null));
        assertThrows(IllegalArgumentException.class, () -> cleanupService.cleanupByLocation(0L));
        assertThrows(IllegalArgumentException.class, () -> cleanupService.cleanupByLocation(-1L));
    }

    private Flow flow(Long flowId, Long locationId, FlowStatus status) {
        return flow(flowId, 1L, locationId, status);
    }

    private Flow flow(Long flowId, Long groupId, Long locationId, FlowStatus status) {
        Flow flow = new Flow(groupId, locationId, "Flow " + flowId, null, status);
        ReflectionTestUtils.setField(flow, "id", flowId);
        return flow;
    }

    private Node eventGateNode(Long nodeId) {
        Node node = new Node(
                100L,
                NodeType.EVENT_GATE,
                JsonNodeFactory.instance.objectNode()
                        .put("requiredCount", 3)
                        .put("countWindowSeconds", 300)
                        .put("cooldownSeconds", 30)
        );
        ReflectionTestUtils.setField(node, "id", nodeId);
        return node;
    }

    private Node thresholdNode() {
        Node node = new Node(200L, NodeType.THRESHOLD, JsonNodeFactory.instance.objectNode());
        ReflectionTestUtils.setField(node, "id", 2001L);
        return node;
    }
}
