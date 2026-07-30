package com.nhnacademy.insightonruleengine.node.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.node.domain.Node;
import com.nhnacademy.insightonruleengine.node.domain.NodeType;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NodeRepositoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NodeRepository nodeRepository;
    private final EntityManager entityManager;

    @Autowired
    NodeRepositoryTest(NodeRepository nodeRepository, EntityManager entityManager) {
        this.nodeRepository = nodeRepository;
        this.entityManager = entityManager;
    }

    @Test
    @DisplayName("Node를 저장하고 ID로 조회한다")
    void saveAndFindById() throws Exception {
        JsonNode configuration = objectMapper.readTree("""
                    "tvoc": 156.0,
                    "infrared": 6.0,
                    "humidity": 64.0,
                    "co2": 1538.0,
                    "temperature": 23.3,
                    "illumination": 57.0,
                    "activity": 19.0,
                    "pressure": 994.2,
                    "infrared_and_visible": 39.0
                """);
        // fixme Node 생성자 변경과 적용 필요
        Node savedNode = nodeRepository.saveAndFlush(
                new Node(1L, NodeType.SENSOR, "온도 센서", configuration)
        );
        entityManager.clear();

        Node foundNode = nodeRepository.findById(savedNode.getId()).orElseThrow();

        assertEquals(1L, foundNode.getFlowId());
        assertEquals(NodeType.SENSOR, foundNode.getNodeType());
        assertEquals(NodeType.Category.TRIGGER, foundNode.getCategory());
        // fixme Node 생성자 변경과 적용 필요
        assertEquals("온도 센서", foundNode.getName());
        assertEquals(configuration, foundNode.getConfiguration());
    }

    @Test
    @DisplayName("flowId가 일치하는 Node만 조회한다")
    void findByFlowId() {
        // fixme Node 생성자 변경과 적용 필요
        Node firstNode = nodeRepository.save(createNode(1L, NodeType.SENSOR, "센서"));
        Node secondNode = nodeRepository.save(createNode(1L, NodeType.THRESHOLD, "임계값"));
        Node otherFlowNode = nodeRepository.save(createNode(2L, NodeType.ALERT, "다른 Flow"));
        nodeRepository.flush();
        entityManager.clear();

        List<Node> nodes = nodeRepository.findByFlowId(1L);
        List<Node> otherFlowNodes = nodeRepository.findByFlowId(2L);

        assertEquals(2, nodes.size());
        List<Long> nodeIds = nodes.stream().map(Node::getId).toList();
        assertTrue(nodeIds.containsAll(List.of(firstNode.getId(), secondNode.getId())));
        assertEquals(1, otherFlowNodes.size());
        assertEquals(otherFlowNode.getId(), otherFlowNodes.getFirst().getId());
    }

    @Test
    @DisplayName("flowId가 일치하는 Node만 삭제한다")
    void deleteByFlowId() {
        nodeRepository.save(createNode(1L, NodeType.SENSOR, "센서"));
        nodeRepository.save(createNode(1L, NodeType.THRESHOLD, "임계값"));
        Node otherFlowNode = nodeRepository.save(createNode(2L, NodeType.ALERT, "다른 Flow"));
        nodeRepository.flush();
        entityManager.clear();

        nodeRepository.deleteByFlowId(1L);
        entityManager.flush();
        entityManager.clear();

        assertEquals(0, nodeRepository.findByFlowId(1L).size());
        assertEquals(otherFlowNode.getId(), nodeRepository.findByFlowId(2L).getFirst().getId());
    }

    private Node createNode(Long flowId, NodeType nodeType, String name) {
        return new Node(flowId, nodeType, name, objectMapper.createObjectNode()
                .put("value", name));
    }
}
