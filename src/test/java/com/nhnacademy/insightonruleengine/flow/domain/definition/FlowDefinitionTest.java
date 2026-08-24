package com.nhnacademy.insightonruleengine.flow.domain.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlowDefinitionTest {

    @Test
    @DisplayName("생성 후 원본 Node와 Link 목록을 변경해도 실행 Definition은 변하지 않는다")
    void defensiveListCopyTest() {
        List<NodeDefinition> nodes = new ArrayList<>();
        nodes.add(new NodeDefinition(
                10L,
                NodeType.SENSOR,
                JsonNodeFactory.instance.objectNode()
        ));
        List<LinkDefinition> links = new ArrayList<>();
        links.add(new LinkDefinition(100L, 1L, 10L, 20L, "out", "in"));
        FlowDefinition definition = definition(nodes, links);

        nodes.clear();
        links.clear();

        assertEquals(1, definition.nodes().size());
        assertEquals(1, definition.links().size());
    }

    @Test
    @DisplayName("실행 Definition이 반환한 Node와 Link 목록은 수정할 수 없다")
    void immutableListTest() {
        FlowDefinition definition = definition(
                List.of(new NodeDefinition(
                        10L,
                        NodeType.SENSOR,
                        JsonNodeFactory.instance.objectNode()
                )),
                List.of(new LinkDefinition(100L, 1L, 10L, 20L, "out", "in"))
        );

        List<NodeDefinition> nodes = definition.nodes();
        NodeDefinition alertNode = new NodeDefinition(
                20L,
                NodeType.ALERT,
                JsonNodeFactory.instance.objectNode()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> nodes.add(alertNode)
        );

        List<LinkDefinition> links = definition.links();
        assertThrows(
                UnsupportedOperationException.class,
                links::clear
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
                "불변성 테스트",
                FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-07-30T00:00:00Z"),
                nodes,
                links
        );
    }
}
