package com.nhnacademy.insightonruleengine.flow.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NodeDefinitionTest {

    @Test
    @DisplayName("원본과 반환 configuration을 변경해도 실행 Node configuration은 변하지 않는다")
    void defensiveConfigurationCopyTest() {
        ObjectNode original = JsonNodeFactory.instance.objectNode();
        original.put("threshold", 30);
        NodeDefinition definition = new NodeDefinition(10L, NodeType.THRESHOLD, original);

        original.put("threshold", 40);
        ObjectNode returned = (ObjectNode) definition.configuration();
        returned.put("threshold", 50);

        assertEquals(30, definition.configuration().get("threshold").asInt());
    }
}
