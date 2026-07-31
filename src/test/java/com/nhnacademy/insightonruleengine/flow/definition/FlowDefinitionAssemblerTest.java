package com.nhnacademy.insightonruleengine.flow.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.exception.FlowNotActiveException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowNotFoundException;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import com.nhnacademy.insightonruleengine.node.domain.Link;
import com.nhnacademy.insightonruleengine.node.domain.Node;
import com.nhnacademy.insightonruleengine.node.domain.NodeType;
import com.nhnacademy.insightonruleengine.node.repository.LinkRepository;
import com.nhnacademy.insightonruleengine.node.repository.NodeRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlowDefinitionAssemblerTest {

    private static final Long GROUP_ID = 2L;
    private static final Long FLOW_ID = 1L;

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private Flow flow;

    @Mock
    private Node node;

    @Mock
    private Link link;

    @InjectMocks
    private FlowDefinitionAssembler assembler;

    @Test
    @DisplayName("Flow와 Node와 Link를 각각 한 번 조회해 실행 Definition으로 조립한다")
    void definitionAssemblyTest() {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-07-30T00:00:00Z");
        when(flow.getId()).thenReturn(FLOW_ID);
        when(flow.getGroupId()).thenReturn(GROUP_ID);
        when(flow.getLocationId()).thenReturn(3L);
        when(flow.getName()).thenReturn("온도 경고");
        when(flow.getDescription()).thenReturn("30도 이상 경고");
        when(flow.getStatus()).thenReturn(FlowStatus.ACTIVE);
        when(flow.getCreatedDate()).thenReturn(createdAt);
        when(flowRepository.findById(FLOW_ID)).thenReturn(Optional.of(flow));
        when(nodeRepository.findByFlowId(FLOW_ID)).thenReturn(List.of(node));
        when(linkRepository.findByFlowId(FLOW_ID)).thenReturn(List.of(link));
        when(node.getId()).thenReturn(10L);
        when(node.getNodeType()).thenReturn(NodeType.SENSOR);
        when(node.getConfiguration()).thenReturn(JsonNodeFactory.instance.objectNode());
        when(link.getId()).thenReturn(100L);
        when(link.getFlowId()).thenReturn(FLOW_ID);
        when(link.getSourceNodeId()).thenReturn(10L);
        when(link.getTargetNodeId()).thenReturn(20L);
        when(link.getSourcePort()).thenReturn("out");
        when(link.getTargetPort()).thenReturn("in");

        FlowDefinition result = assembler.assemble(GROUP_ID, FLOW_ID);

        assertEquals(FLOW_ID, result.flowId());
        assertEquals(GROUP_ID, result.groupId());
        assertEquals(3L, result.locationId());
        assertEquals("온도 경고", result.name());
        assertEquals(FlowStatus.ACTIVE, result.status());
        assertEquals(createdAt, result.createdAt());
        assertEquals(1, result.nodes().size());
        assertEquals(10L, result.nodes().getFirst().nodeId());
        assertEquals(1, result.links().size());
        assertEquals(100L, result.links().getFirst().linkId());
        verify(flowRepository).findById(FLOW_ID);
        verify(nodeRepository).findByFlowId(FLOW_ID);
        verify(linkRepository).findByFlowId(FLOW_ID);
    }

    @Test
    @DisplayName("요청 그룹에 속하지 않는 Flow는 Node와 Link 조회 전에 거부한다")
    void otherGroupFlowTest() {
        when(flow.getGroupId()).thenReturn(GROUP_ID);
        when(flowRepository.findById(FLOW_ID)).thenReturn(Optional.of(flow));

        assertThrows(
                FlowNotFoundException.class,
                () -> assembler.assemble(99L, FLOW_ID)
        );

        verify(flowRepository).findById(FLOW_ID);
        verifyNoInteractions(nodeRepository, linkRepository);
    }

    @Test
    @DisplayName("ACTIVE가 아닌 Flow의 실행용 Definition 조립을 거부한다")
    void inactiveFlowTest() {
        when(flow.getId()).thenReturn(FLOW_ID);
        when(flow.getGroupId()).thenReturn(GROUP_ID);
        when(flow.getStatus()).thenReturn(FlowStatus.INACTIVE);
        when(flowRepository.findById(FLOW_ID)).thenReturn(Optional.of(flow));
        when(nodeRepository.findByFlowId(FLOW_ID)).thenReturn(List.of());
        when(linkRepository.findByFlowId(FLOW_ID)).thenReturn(List.of());

        FlowNotActiveException exception =
                assertThrows(
                        FlowNotActiveException.class,
                        () -> assembler.assembleActive(GROUP_ID, FLOW_ID)
                );

        assertEquals(
                "Active 상태의 플로우만 활동 가능합니다. flowId=1, status=INACTIVE",
                exception.getMessage()
        );
        verify(flowRepository).findById(FLOW_ID);
        verify(nodeRepository).findByFlowId(FLOW_ID);
        verify(linkRepository).findByFlowId(FLOW_ID);
    }

    @Test
    @DisplayName("ACTIVE Flow는 실행용 Definition으로 조립한다")
    void activeFlowTest() {
        when(flow.getId()).thenReturn(FLOW_ID);
        when(flow.getGroupId()).thenReturn(GROUP_ID);
        when(flow.getStatus()).thenReturn(FlowStatus.ACTIVE);
        when(flowRepository.findById(FLOW_ID)).thenReturn(Optional.of(flow));
        when(nodeRepository.findByFlowId(FLOW_ID)).thenReturn(List.of());
        when(linkRepository.findByFlowId(FLOW_ID)).thenReturn(List.of());

        FlowDefinition result = assembler.assembleActive(GROUP_ID, FLOW_ID);

        assertEquals(FlowStatus.ACTIVE, result.status());
        verify(flowRepository).findById(FLOW_ID);
        verify(nodeRepository).findByFlowId(FLOW_ID);
        verify(linkRepository).findByFlowId(FLOW_ID);
    }
}
