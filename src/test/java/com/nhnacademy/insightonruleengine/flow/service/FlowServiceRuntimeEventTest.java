package com.nhnacademy.insightonruleengine.flow.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.FlowTestData;
import com.nhnacademy.insightonruleengine.flow.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.authorization.GroupRole;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.dto.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.event.FlowRuntimeChangeEvent;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.repository.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.repository.NodeRepository;
import com.nhnacademy.insightonruleengine.flow.validation.FlowActivationValidator;
import com.nhnacademy.insightonruleengine.flow.validation.FlowStructureValidator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FlowServiceRuntimeEventTest {

    private static final long FLOW_ID = 10L;
    private static final long ALERT_NODE_ID = 100L;

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
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FlowService flowService;

    @Test
    @DisplayName("활성화와 비활성화는 커밋 후 Redis 동기화 이벤트를 발행합니다.")
    void statusRuntimeEventTest() {
        Flow flow = flow(FlowStatus.INACTIVE);
        when(flowRepository.findById(FLOW_ID)).thenReturn(Optional.of(flow));
        when(flowDefinitionAssembler.assemble(1L, FLOW_ID)).thenReturn(definition());
        when(flowActivationValidator.validate(any(FlowDefinition.class))).thenReturn(List.of());
        when(nodeRepository.findByFlowId(FLOW_ID)).thenReturn(List.of(alertNode()));

        flowService.changeActivationStatus(1L, 100L, FLOW_ID,
                new FlowStatusChangeRequest(FlowStatus.ACTIVE));
        flowService.changeActivationStatus(1L, 100L, FLOW_ID,
                new FlowStatusChangeRequest(FlowStatus.INACTIVE));

        verify(groupAuthorizationService, times(2)).requireRole(1L, 100L, GroupRole.MANAGER);
        verify(eventPublisher).publishEvent(FlowRuntimeChangeEvent.activate(1L, 2L, FLOW_ID));
        verify(eventPublisher).publishEvent(
                FlowRuntimeChangeEvent.remove(1L, 2L, FLOW_ID, Set.of(ALERT_NODE_ID)));
    }

    @Test
    @DisplayName("Flow 수정은 이전 Flow를 Archive 상태로 만들고 런타임 제거 이벤트를 발행합니다.")
    void updateRuntimeEventTest() {
        Flow currentFlow = flow(FlowStatus.ACTIVE);
        when(flowRepository.findById(FLOW_ID)).thenReturn(Optional.of(currentFlow));
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(flowRepository.existsByGroupIdAndLocationIdAndName(1L, 2L, "수정 Flow"))
                .thenReturn(false);
        when(flowStructureValidator.validate(any(), any())).thenReturn(List.of());
        when(nodeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(linkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        flowService.update(1L, 100L, FLOW_ID,
                FlowTestData.createValidUpdateRequest("수정 Flow", null));

        verify(eventPublisher).publishEvent(FlowRuntimeChangeEvent.remove(1L, 2L, FLOW_ID, Set.of()));
    }

    @Test
    @DisplayName("영구 삭제는 자식 Node와 Link 삭제 후 런타임 제거 이벤트를 발행합니다.")
    void deleteRuntimeEventTest() {
        Flow archivedFlow = flow(FlowStatus.ARCHIVED);
        when(flowRepository.findById(FLOW_ID)).thenReturn(Optional.of(archivedFlow));

        flowService.delete(1L, 100L, FLOW_ID);

        InOrder order = inOrder(nodeRepository, linkRepository, flowRepository, eventPublisher);
        order.verify(linkRepository).deleteByFlowId(FLOW_ID);
        order.verify(nodeRepository).deleteByFlowId(FLOW_ID);
        order.verify(flowRepository).delete(archivedFlow);
        order.verify(eventPublisher).publishEvent(
                FlowRuntimeChangeEvent.remove(1L, 2L, FLOW_ID, Set.of()));
    }

    @Test
    @DisplayName("복구는 Flow를 INACTIVE로 전환하므로 런타임 이벤트를 발행하지 않습니다.")
    void restoreEventTest() {
        Flow archivedFlow = flow(FlowStatus.ARCHIVED);
        when(flowRepository.findById(FLOW_ID)).thenReturn(Optional.of(archivedFlow));

        flowService.restore(1L, 100L, FLOW_ID);

        verifyNoInteractions(eventPublisher);
    }

    private Flow flow(FlowStatus flowStatus) {
        Flow flow = new Flow(1L, 2L, "Flow " + FLOW_ID, null, flowStatus);
        ReflectionTestUtils.setField(flow, "id", FLOW_ID);
        return flow;
    }

    private FlowDefinition definition() {
        return new FlowDefinition(
                FLOW_ID,
                1L,
                2L,
                "Flow " + FLOW_ID,
                null,
                FlowStatus.INACTIVE,
                OffsetDateTime.now(),
                List.of(),
                List.of());
    }

    private Node alertNode() {
        Node node = new Node(FLOW_ID, NodeType.ALERT, JsonNodeFactory.instance.objectNode());
        ReflectionTestUtils.setField(node, "id", ALERT_NODE_ID);
        return node;
    }
}
