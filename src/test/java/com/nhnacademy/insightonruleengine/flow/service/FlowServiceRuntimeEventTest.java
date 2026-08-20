package com.nhnacademy.insightonruleengine.flow.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.flow.FlowTestData;
import com.nhnacademy.insightonruleengine.flow.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.cache.ActiveFlowDefinitionProvider;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.dto.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.repository.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.repository.NodeRepository;
import com.nhnacademy.insightonruleengine.flow.validation.FlowActivationValidator;
import com.nhnacademy.insightonruleengine.flow.validation.FlowStructureValidator;
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
    private FlowDefinitionAssembler flowDefinitionAssembler;

    @Mock
    private FlowActivationValidator flowActivationValidator;

    @Mock
    private ActiveFlowDefinitionProvider activeFlowDefinitionProvider;

    @InjectMocks
    private FlowService flowService;

    @Test
    @DisplayName("활성화와 비활성화는 캐시 제공자에 서로 다른 동기화 작업을 요청합니다.")
    void statusCacheSynchronizationTest() {
        Flow flow = flow(10L, FlowStatus.INACTIVE);
        when(flowRepository.findById(10L)).thenReturn(Optional.of(flow));
        when(flowDefinitionAssembler.assemble(1L, 10L)).thenReturn(definition(10L));
        when(flowActivationValidator.validate(any(FlowDefinition.class))).thenReturn(List.of());

        flowService.changeActivationStatus(1L, 100L, 10L,
                new FlowStatusChangeRequest(FlowStatus.ACTIVE));
        flowService.changeActivationStatus(1L, 100L, 10L,
                new FlowStatusChangeRequest(FlowStatus.INACTIVE));

        verify(activeFlowDefinitionProvider).refreshAfterCommit(1L, 2L);
        verify(activeFlowDefinitionProvider).evictAfterCommit(1L, 2L);
    }

    @Test
    @DisplayName("Flow 수정은 이전 Flow를 Archive 상태로 만들고 새 Inactive Flow를 캐시에 반영합니다.")
    void updateCacheSynchronizationTest() {
        Flow currentFlow = flow(10L, FlowStatus.ACTIVE);
        when(flowRepository.findById(10L)).thenReturn(Optional.of(currentFlow));
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(flowRepository.existsByGroupIdAndLocationIdAndName(1L, 2L, "수정 Flow"))
                .thenReturn(false);
        when(flowStructureValidator.validate(any(), any())).thenReturn(List.of());
        when(nodeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(linkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        flowService.update(1L, 100L, 10L,
                FlowTestData.createValidUpdateRequest("수정 Flow", null));

        verify(activeFlowDefinitionProvider).refreshAfterCommit(1L, 2L);
    }

    @Test
    @DisplayName("영구 삭제는 자식 Node와 Link 삭제 후 캐시 제거를 요청합니다.")
    void deleteCacheSynchronizationTest() {
        Flow archivedFlow = flow(10L, FlowStatus.ARCHIVED);
        when(flowRepository.findById(10L)).thenReturn(Optional.of(archivedFlow));

        flowService.delete(1L, 100L, 10L);

        InOrder order = inOrder(nodeRepository, linkRepository, flowRepository,
                activeFlowDefinitionProvider);
        order.verify(linkRepository).deleteByFlowId(10L);
        order.verify(nodeRepository).deleteByFlowId(10L);
        order.verify(flowRepository).delete(archivedFlow);
        order.verify(activeFlowDefinitionProvider).evictAfterCommit(1L, 2L);
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
