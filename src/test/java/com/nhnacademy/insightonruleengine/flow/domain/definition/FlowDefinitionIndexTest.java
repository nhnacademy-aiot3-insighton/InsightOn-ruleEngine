package com.nhnacademy.insightonruleengine.flow.domain.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.exception.DuplicateFlowDefinitionKeyException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.LinkNotFoundException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.NodeNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlowDefinitionIndexTest {

    @Test
    @DisplayName("저장 Node ID로 실행 Node를 조회한다")
    void nodeLookupTest() {
        NodeDefinition node = node(10L, NodeType.SENSOR);
        FlowDefinitionIndex index = new FlowDefinitionIndex(definition(List.of(node), List.of()));

        NodeDefinition result = index.requireNode(10L);

        assertEquals(node, result);
    }

    @Test
    @DisplayName("Source Node와 Port로 다음 Link를 조회한다")
    void sourcePortLookupTest() {
        LinkDefinition link = link(100L, 10L, "out", 20L);
        FlowDefinitionIndex index = new FlowDefinitionIndex(definition(
                List.of(node(10L, NodeType.SENSOR), node(20L, NodeType.ALERT)),
                List.of(link)
        ));

        List<LinkDefinition> result = index.requireLinks(10L, "out");

        assertEquals(List.of(link), result);
    }

    @Test
    @DisplayName("존재하지 않는 Node ID 조회는 식별 가능한 예외를 발생시킨다")
    void missingNodeTest() {
        FlowDefinitionIndex index = new FlowDefinitionIndex(definition(List.of(), List.of()));

        NodeNotFoundException exception =
                assertThrows(NodeNotFoundException.class, () -> index.requireNode(10L));

        assertEquals("노드를 찾을 수 없습니다. nodeId=10", exception.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 Source Port 조회는 식별 가능한 예외를 발생시킨다")
    void missingLinkTest() {
        FlowDefinitionIndex index = new FlowDefinitionIndex(definition(
                List.of(node(10L, NodeType.SENSOR)),
                List.of()
        ));

        LinkNotFoundException exception =
                assertThrows(LinkNotFoundException.class, () -> index.requireLinks(10L, "out"));

        assertEquals(
                "링크를 찾을 수 없습니다. sourceNodeId=10, sourcePort=out",
                exception.getMessage()
        );
    }

    @Test
    @DisplayName("null FlowDefinition은 인덱스 생성 전에 거부한다")
    void nullDefinitionTest() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> new FlowDefinitionIndex(null));

        assertEquals("flowDefinition는 null이면 안됩니다.", exception.getMessage());
    }

    @Test
    @DisplayName("null Node ID는 Map 조회 전에 거부한다")
    void nullNodeIdTest() {
        FlowDefinitionIndex index = new FlowDefinitionIndex(definition(List.of(), List.of()));

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> index.requireNode(null));

        assertEquals("nodeId는 필수입니다.", exception.getMessage());
    }

    @Test
    @DisplayName("null Source Node ID는 Link Key 생성 전에 거부한다")
    void nullSourceNodeIdTest() {
        FlowDefinitionIndex index = new FlowDefinitionIndex(definition(List.of(), List.of()));

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> index.requireLinks(null, "out"));

        assertEquals("sourceNodeId는 null이면 안됩니다.", exception.getMessage());
    }

    @Test
    @DisplayName("null Source Port는 Link Key 생성 전에 거부한다")
    void nullSourcePortTest() {
        FlowDefinitionIndex index = new FlowDefinitionIndex(definition(List.of(), List.of()));

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> index.requireLinks(10L, null));

        assertEquals("sourcePort는 null이면 안됩니다.", exception.getMessage());
    }

    @Test
    @DisplayName("중복 Node ID는 기존 인덱스 값을 덮어쓰지 않고 거부한다")
    void duplicateNodeIdTest() {
        FlowDefinition definition = definition(
                List.of(node(10L, NodeType.SENSOR), node(10L, NodeType.ALERT)),
                List.of()
        );

        DuplicateFlowDefinitionKeyException exception =
                assertThrows(
                        DuplicateFlowDefinitionKeyException.class,
                        () -> new FlowDefinitionIndex(definition)
                );

        assertEquals(
                "FlowDefinition에 중복된 Node ID가 있습니다: nodeId=10",
                exception.getMessage()
        );
    }

    @Test
    @DisplayName("같은 Source Node와 Port의 복수 Link를 요청 순서대로 색인한다")
    void actionFanOutLinkTest() {
        FlowDefinition definition = definition(
                List.of(
                        node(10L, NodeType.SENSOR),
                        node(20L, NodeType.ALERT),
                        node(30L, NodeType.ALERT)
                ),
                List.of(
                        link(100L, 10L, "out", 20L),
                        link(101L, 10L, "out", 30L)
                )
        );
        FlowDefinitionIndex index = new FlowDefinitionIndex(definition);

        assertEquals(definition.links(), index.requireLinks(10L, "out"));
        assertEquals(definition.links(), index.findLinks(10L, "out"));
    }

    @Test
    @DisplayName("출발·도착 Node와 Port가 모두 같은 중복 Link는 거부한다")
    void duplicateLinkTest() {
        LinkDefinition duplicate = link(100L, 10L, "out", 20L);
        FlowDefinition definition = definition(
                List.of(node(10L, NodeType.SENSOR), node(20L, NodeType.ALERT)),
                List.of(duplicate, link(101L, 10L, "out", 20L))
        );

        DuplicateFlowDefinitionKeyException exception = assertThrows(
                DuplicateFlowDefinitionKeyException.class,
                () -> new FlowDefinitionIndex(definition)
        );

        assertEquals(
                "FlowDefinition에 중복된 Link가 있습니다: sourceNodeId=10, sourcePort=out, "
                        + "targetNodeId=20, targetPort=in",
                exception.getMessage()
        );
    }

    private FlowDefinition definition(
            List<NodeDefinition> nodes,
            List<LinkDefinition> links
    ) {
        return new FlowDefinition(
                1L,
                2L,
                3L,
                "테스트 Flow",
                "인덱스 테스트",
                FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-07-30T00:00:00Z"),
                nodes,
                links
        );
    }

    private NodeDefinition node(Long nodeId, NodeType nodeType) {
        return new NodeDefinition(nodeId, nodeType, JsonNodeFactory.instance.objectNode());
    }

    private LinkDefinition link(
            Long linkId,
            Long sourceNodeId,
            String sourcePort,
            Long targetNodeId
    ) {
        return new LinkDefinition(linkId, 1L, sourceNodeId, targetNodeId, sourcePort, "in");
    }
}
