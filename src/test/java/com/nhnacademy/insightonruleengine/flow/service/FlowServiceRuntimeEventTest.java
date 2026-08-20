package com.nhnacademy.insightonruleengine.flow.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.FlowTestData;
import com.nhnacademy.insightonruleengine.flow.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.dto.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.event.FlowRuntimeChangeEvent;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.repository.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.repository.NodeRepository;
import com.nhnacademy.insightonruleengine.flow.validation.FlowStructureValidator;
import com.nhnacademy.insightonruleengine.flow.validation.NodeConfigurationValidator;
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
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FlowService flowService;

    @Test
    @DisplayName("활성화와 비활성화는 서로 다른 Redis 동기화 이벤트를 발행합니다.")
    void statusEventTest() {
        Flow flow = flow(10L, FlowStatus.INACTIVE);
        Node alertNode = alertNode(10L, 100L);
        when(flowRepository.findById(10L)).thenReturn(Optional.of(flow));
        when(nodeRepository.findByFlowId(10L)).thenReturn(List.of(alertNode));

        flowService.changeActivationStatus(1L, 100L, 10L, new FlowStatusChangeRequest(FlowStatus.ACTIVE));
        flowService.changeActivationStatus(1L, 100L, 10L, new FlowStatusChangeRequest(FlowStatus.INACTIVE));

        verify(eventPublisher).publishEvent(FlowRuntimeChangeEvent.activate(1L, 2L, 10L));
        verify(eventPublisher).publishEvent(FlowRuntimeChangeEvent.remove(1L, 2L, 10L, Set.of(100L)));
    }

    @Test
    @DisplayName("Flow 수정은 이전 Flow를 Archive 상태로 만들고 새 Inactive Flow는 이벤트는 발행하지 않고 실행은 하지 않습니다.")
    void updateEventTest() {
        Flow currentFlow = flow(10L, FlowStatus.ACTIVE);
        Node oldAlertNode = alertNode(10L, 100L);
        when(flowRepository.findById(10L)).thenReturn(Optional.of(currentFlow));
        when(flowRepository.save(any(Flow.class))).thenReturn(flow(20L, FlowStatus.INACTIVE));
        when(nodeRepository.save(any(Node.class))).thenReturn(alertNode(20L, 200L));
        when(nodeRepository.findByFlowId(10L)).thenReturn(List.of(oldAlertNode));

        flowService.update(
                1L,
                100L,
                10L,
                FlowTestData.createValidUpdateRequest("수정 Flow", null)
        );

        verify(eventPublisher).publishEvent(
                FlowRuntimeChangeEvent.remove(1L, 2L, 10L, Set.of(100L))
        );
    }

    @Test
    @DisplayName("영구 삭제는 Node 삭제 전 id를 제거 이벤트에 보존합니다.")
    void deleteEventTest() {
        Flow archivedFlow = flow(10L, FlowStatus.ARCHIVED);
        Node alertNode = alertNode(10L, 100L);
        when(flowRepository.findById(10L)).thenReturn(Optional.of(archivedFlow));
        when(nodeRepository.findByFlowId(10L)).thenReturn(List.of(alertNode));

        flowService.delete(1L, 100L, 10L);

        InOrder order = inOrder(nodeRepository, linkRepository, flowRepository, eventPublisher);
        order.verify(nodeRepository).findByFlowId(10L);
        order.verify(linkRepository).deleteByFlowId(10L);
        order.verify(nodeRepository).deleteByFlowId(10L);
        order.verify(flowRepository).delete(archivedFlow);
        order.verify(eventPublisher).publishEvent(
                FlowRuntimeChangeEvent.remove(1L, 2L, 10L, Set.of(100L))
        );
    }

    @Test
    @DisplayName("복구는 Flow를 INACTIVE로 전환하므로 런타임 이벤트를 발행하지 않습니다.")
    void restoreEventTest() {
        Flow archivedFlow = flow(10L, FlowStatus.ARCHIVED);
        when(flowRepository.findById(10L)).thenReturn(Optional.of(archivedFlow));

        flowService.restore(1L, 100L, 10L);

        verify(eventPublisher, never()).publishEvent(any());
    }

    private Flow flow(Long flowId, FlowStatus flowStatus) {
        Flow flow = new Flow(1L, 2L, "Flow " + flowId, null, flowStatus);
        ReflectionTestUtils.setField(flow, "id", flowId);
        return flow;
    }

    private Node alertNode(Long flowId, Long nodeId) {
        Node node = new Node(
                flowId,
                NodeType.ALERT,
                JsonNodeFactory.instance.objectNode()
        );
        ReflectionTestUtils.setField(node, "id", nodeId);
        return node;
    }
}
