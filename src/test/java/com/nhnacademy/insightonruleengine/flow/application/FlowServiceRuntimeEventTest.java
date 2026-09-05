package com.nhnacademy.insightonruleengine.flow.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.flow.FlowTestData;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.application.assembly.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.application.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowActivationValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowStructureValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.NodeConfigurationValidator;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.NodeRepository;
import com.nhnacademy.insightonruleengine.runner.application.schedule.ScheduleFlowScheduler;
import com.nhnacademy.insightonruleengine.runner.infrastructure.cache.ActiveFlowDefinitionProvider;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FlowServiceRuntimeEventTest {

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private GroupAuthorizationService groupAuthorizationService;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private FlowStructureValidator flowStructureValidator;

    @Mock
    private NodeConfigurationValidator nodeConfigurationValidator;

    @Mock
    private FlowDefinitionAssembler flowDefinitionAssembler;

    @Mock
    private FlowActivationValidator flowActivationValidator;

    @Mock
    private ActiveFlowDefinitionProvider activeFlowDefinitionProvider;

    @Mock
    private ScheduleFlowScheduler scheduleFlowScheduler;

    @InjectMocks
    private FlowService flowService;

    @Test
    @DisplayName("활성화와 비활성화는 장소의 ACTIVE Flow 목록을 다시 캐싱합니다.")
    void statusCacheSynchronizationTest() {
        Flow flow = flow(10L, FlowStatus.INACTIVE);
        when(flowRepository.findById(10L)).thenReturn(Optional.of(flow));
        when(flowDefinitionAssembler.assemble(1L, 10L)).thenReturn(definition(10L));
        when(flowActivationValidator.validate(any(FlowDefinition.class))).thenReturn(List.of());

        flowService.changeActivationStatus(1L, 100L, 10L,
                new FlowStatusChangeRequest(FlowStatus.ACTIVE));
        flowService.changeActivationStatus(1L, 100L, 10L,
                new FlowStatusChangeRequest(FlowStatus.INACTIVE));

        verify(activeFlowDefinitionProvider, times(2))
                .refreshAfterCommit(1L, 2L);
        verify(scheduleFlowScheduler).registerAfterCommit(1L, 10L);
        verify(scheduleFlowScheduler).cancelAfterCommit(10L);
    }

    @Test
    @DisplayName("Flow 수정은 이전 Flow를 Archive 상태로 만들고 새 Inactive Flow를 캐시에 반영합니다.")
    void updateCacheSynchronizationTest() {
        Flow currentFlow = flow(10L, FlowStatus.ACTIVE);
        when(flowRepository.findById(10L)).thenReturn(Optional.of(currentFlow));
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(flowRepository.existsByGroupIdAndLocationIdAndNameAndStatusNot(
                1L, 2L, "수정 Flow", FlowStatus.ARCHIVED))
                .thenReturn(false);
        when(flowStructureValidator.validate(any(), any())).thenReturn(List.of());
        when(nodeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(linkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        flowService.update(1L, 100L, 10L,
                FlowTestData.createValidUpdateRequest("수정 Flow", null));

        verify(activeFlowDefinitionProvider).refreshAfterCommit(1L, 2L);
        verify(scheduleFlowScheduler).cancelAfterCommit(10L);
    }

    @Test
    @DisplayName("Flow 보관은 기존 Schedule 등록을 취소합니다.")
    void archiveScheduleCancellationTest() {
        Flow activeFlow = flow(10L, FlowStatus.ACTIVE);
        when(flowRepository.findById(10L)).thenReturn(Optional.of(activeFlow));

        flowService.archive(1L, 100L, 10L);

        verify(scheduleFlowScheduler).cancelAfterCommit(10L);
    }

    @Test
    @DisplayName("영구 삭제는 자식 Node와 Link 삭제 후 장소 캐시를 갱신합니다.")
    void deleteCacheSynchronizationTest() {
        Flow archivedFlow = flow(10L, FlowStatus.ARCHIVED);
        when(flowRepository.findById(10L)).thenReturn(Optional.of(archivedFlow));

        flowService.delete(1L, 100L, 10L);

        InOrder order = inOrder(nodeRepository, linkRepository, flowRepository,
                activeFlowDefinitionProvider);
        order.verify(linkRepository).deleteByFlowId(10L);
        order.verify(nodeRepository).deleteByFlowId(10L);
        order.verify(flowRepository).delete(archivedFlow);
        order.verify(activeFlowDefinitionProvider).refreshAfterCommit(1L, 2L);
        verify(scheduleFlowScheduler).cancelAfterCommit(10L);
    }

    @Test
    @DisplayName("복구는 Flow를 INACTIVE로 전환하므로 런타임 이벤트를 발행하지 않습니다.")
    void restoreEventTest() {
        Flow archivedFlow = flow(10L, FlowStatus.ARCHIVED);
        when(flowRepository.findById(10L)).thenReturn(Optional.of(archivedFlow));

        flowService.restore(1L, 100L, 10L);

        verifyNoInteractions(activeFlowDefinitionProvider);
    }

    private Flow flow(Long flowId, FlowStatus flowStatus) {
        Flow flow = new Flow(1L, 2L, "Flow " + flowId, null, flowStatus);
        ReflectionTestUtils.setField(flow, "id", flowId);
        return flow;
    }

    private FlowDefinition definition(Long flowId) {
        return new FlowDefinition(
                flowId,
                1L,
                2L,
                "Flow " + flowId,
                null,
                FlowStatus.INACTIVE,
                OffsetDateTime.now(),
                List.of(),
                List.of());
    }
}
