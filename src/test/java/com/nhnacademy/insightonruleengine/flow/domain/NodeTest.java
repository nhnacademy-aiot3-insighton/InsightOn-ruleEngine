package com.nhnacademy.insightonruleengine.flow.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NodeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Node 생성 시 입력값을 보존하고 NodeType에서 category를 계산한다")
    void createNode() {
        JsonNode configuration = objectMapper.createObjectNode()
                .put("sensorId", 10L)
                .put("metric", "temperature");

        Node node = new Node(1L, NodeType.SENSOR, configuration);

        assertEquals(1L, node.getFlowId());
        assertEquals(NodeType.SENSOR, node.getNodeType());
        assertEquals(NodeType.Category.TRIGGER, node.getCategory());
        assertEquals(configuration, node.getConfiguration());
    }

    @Test
    @DisplayName("Node 설정을 변경할 수 있다")
    void updateNode() {
        Node node = new Node(1L, NodeType.THRESHOLD, objectMapper.createObjectNode()
                .put("threshold", 30));
        JsonNode newConfiguration = objectMapper.createObjectNode()
                .put("threshold", 35);

        node.updateConfiguration(newConfiguration);

        assertEquals(newConfiguration, node.getConfiguration());
    }

    @Test
    @DisplayName("저장 전 Node는 서로 같은 객체로 취급하지 않는다")
    void transientNodeEquality() {
        Node source = new Node(1L, NodeType.SENSOR, objectMapper.createObjectNode()
                .put("sensorId", 1L));
        Node target = new Node(1L, NodeType.SENSOR, objectMapper.createObjectNode()
                .put("sensorId", 1L));

        assertNotEquals(source, target);
        assertEquals(source, source);
    }

    @Test
    @DisplayName("Node equals는 식별자가 있을 때 같은 ID를 기준으로 판단한다")
    void persistedNodeEquality() {
        Node source = new Node(1L, NodeType.SENSOR, objectMapper.createObjectNode()
                .put("sensorId", 1L));
        Node target = new Node(2L, NodeType.ALERT, objectMapper.createObjectNode()
                .put("message", "alert"));
        ReflectionTestUtils.setField(source, "id", 10L);
        ReflectionTestUtils.setField(target, "id", 10L);

        assertEquals(source, target);
        assertEquals(source.hashCode(), target.hashCode());
    }
}
