package com.nhnacademy.insightonruleengine.flow.service;

import static com.nhnacademy.insightonruleengine.flow.FlowTestData.createValidLinks;
import static com.nhnacademy.insightonruleengine.flow.FlowTestData.createValidNodes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.FlowTestData;
import com.nhnacademy.insightonruleengine.flow.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.Link;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.dto.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.dto.FlowUpdateRequest;
import com.nhnacademy.insightonruleengine.flow.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.exception.InvalidFlowStructureException;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.repository.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.repository.NodeRepository;
import com.nhnacademy.insightonruleengine.flow.validation.FlowLinkValidator;
import com.nhnacademy.insightonruleengine.flow.validation.FlowNodeValidator;
import com.nhnacademy.insightonruleengine.flow.validation.FlowPathValidator;
import com.nhnacademy.insightonruleengine.flow.validation.FlowStructureValidator;
import com.nhnacademy.insightonruleengine.flow.validation.LinkValidator;
import com.nhnacademy.insightonruleengine.flow.validation.NodeConfigurationValidator;
import com.nhnacademy.insightonruleengine.flow.validation.NodeValidator;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        FlowService.class,
        NodeValidator.class,
        LinkValidator.class,
        FlowNodeValidator.class,
        FlowLinkValidator.class,
        FlowPathValidator.class,
        FlowStructureValidator.class
})
class FlowServiceIntegrationTest {

    @Autowired
    private FlowService flowService;

    @Autowired
    private FlowRepository flowRepository;

    @MockitoSpyBean
    private NodeRepository nodeRepository;

    @MockitoSpyBean
    private LinkRepository linkRepository;

    @MockitoBean
    private GroupAuthorizationService groupAuthorizationService;

    @MockitoBean
    private NodeConfigurationValidator nodeConfigurationValidator;

    @Autowired
    private EntityManager entityManager;


    @Test
    @DisplayName("Flow 수정은 기존 Flow를 보관하고 새 ID의 INACTIVE Flow를 저장한다")
    void updateCreatesNewFlowId() {
        Flow currentFlow = flowRepository.saveAndFlush(
                new Flow(1L, 10L, "온도 경고 v1", null, FlowStatus.ACTIVE)
        );
        Long currentFlowId = currentFlow.getId();

        Node oldSensor = nodeRepository.save(
                new Node(
                        currentFlowId,
                        NodeType.SENSOR,
                        JsonNodeFactory.instance.objectNode().put("devName", "old-sensor")
                )
        );
        Node oldAlert = nodeRepository.save(
                new Node(
                        currentFlowId,
                        NodeType.ALERT,
                        JsonNodeFactory.instance.objectNode()
                                .put("title", "이전 온도 경고")
                                .put("severity", "WARNING")
                                .put("message", "이전 경고 메시지")
                )
        );
        nodeRepository.flush();
        Link oldLink = linkRepository.saveAndFlush(
                new Link(currentFlowId, oldSensor.getId(), "out", oldAlert.getId(), "in")
        );
        FlowResponse response = flowService.update(
                1L,
                100L,
                currentFlowId,
                FlowTestData.createValidUpdateRequest(" 온도 경고 v2 ", "수정 설명")
        );
        entityManager.flush();
        entityManager.clear();

        Flow archivedFlow = flowRepository.findById(currentFlowId).orElseThrow();
        Flow updatedFlow = flowRepository.findById(response.flowId()).orElseThrow();

        assertNotEquals(currentFlowId, response.flowId());
        assertEquals(FlowStatus.ARCHIVED, archivedFlow.getStatus());
        assertEquals(FlowStatus.INACTIVE, updatedFlow.getStatus());
        assertEquals(10L, updatedFlow.getLocationId());
        assertEquals("온도 경고 v2", updatedFlow.getName());
        assertEquals(2, nodeRepository.findByFlowId(currentFlowId).size());
        assertEquals(oldLink.getId(), linkRepository.findByFlowId(currentFlowId).getFirst().getId());

        List<Node> updatedNodes = nodeRepository.findByFlowId(response.flowId());
        Link updatedLink = linkRepository.findByFlowId(response.flowId()).getFirst();
        assertEquals(2, updatedNodes.size());
        assertTrue(updatedNodes.stream().anyMatch(node -> node.getId().equals(updatedLink.getSourceNodeId())));
        assertTrue(updatedNodes.stream().anyMatch(node -> node.getId().equals(updatedLink.getTargetNodeId())));

    }

    @Test
    @DisplayName("Flow 복구는 새 행 없이 기존 ID를 INACTIVE로 전환한다")
    void restoreFlowInactiveTest() {
        Flow archivedFlow = flowRepository.saveAndFlush(
                new Flow(1L, 10L, "온도 경고", null, FlowStatus.ARCHIVED)
        );
        Long archivedFlowId = archivedFlow.getId();
        long beforeCount = flowRepository.count();

        FlowResponse response = flowService.restore(1L, 100L, archivedFlowId);
        entityManager.flush();
        entityManager.clear();

        Flow restoredFlow = flowRepository.findById(archivedFlowId).orElseThrow();

        assertEquals(beforeCount, flowRepository.count());
        assertEquals(archivedFlowId, response.flowId());
        assertEquals(FlowStatus.INACTIVE, restoredFlow.getStatus());
    }

    @Test
    @DisplayName("Flow 수정 검증 실패 시 기존 Flow 상태를 유지한다")
    void failedUpdateTest() {
        Flow currentFlow = flowRepository.save(
                new Flow(1L, 10L, "온도 경고 v1", null, FlowStatus.ACTIVE)
        );
        flowRepository.saveAndFlush(
                new Flow(1L, 10L, "온도 경고 v2", null, FlowStatus.ARCHIVED)
        );
        Long currentFlowId = currentFlow.getId();
        FlowUpdateRequest updateRequest = FlowTestData.createValidUpdateRequest("온도 경고 v2", null);

        assertThrows(
                DuplicateFlowNameException.class,
                () -> flowService.update(
                        1L,
                        100L,
                        currentFlowId,
                        updateRequest
                )
        );
        entityManager.flush();
        entityManager.clear();

        Flow unchangedFlow = flowRepository.findById(currentFlowId).orElseThrow();

        assertEquals(FlowStatus.ACTIVE, unchangedFlow.getStatus());
        assertEquals(2L, flowRepository.count());
    }

    // 실제 구조 검증기가 저장 전에 요청을 거부해 기존 Flow와 저장 데이터를 보호하는지 확인합니다.
    @Test
    @DisplayName("Flow 구조 검증 실패 시 어떤 구성 데이터도 저장하지 않는다")
    void notSaveTest() {
        Flow currentFlow = flowRepository.saveAndFlush(
                new Flow(1L, 10L, "온도 경고 v1", null, FlowStatus.ACTIVE)
        );
        Long currentFlowId = currentFlow.getId();
        FlowUpdateRequest invalidRequest = FlowUpdateRequest.builder()
                .name("온도 경고 v2")
                .nodes(List.of(
                        FlowNodeRequest.builder()
                                .clientNodeKey("same")
                                .nodeType(NodeType.SENSOR)
                                .configuration(JsonNodeFactory.instance.objectNode())
                                .build(),
                        FlowNodeRequest.builder()
                                .clientNodeKey("same")
                                .nodeType(NodeType.ALERT)
                                .configuration(JsonNodeFactory.instance.objectNode())
                                .build()))
                .links(List.of(FlowLinkRequest.builder()
                        .sourceClientNodeKey("same")
                        .targetClientNodeKey("same")
                        .sourcePort("out")
                        .targetPort("in")
                        .build()))
                .build();

        assertThrows(
                InvalidFlowStructureException.class,
                () -> flowService.update(1L, 100L, currentFlowId, invalidRequest)
        );
        entityManager.flush();
        entityManager.clear();

        Flow unchangedFlow = flowRepository.findById(currentFlowId).orElseThrow();

        assertEquals(FlowStatus.ACTIVE, unchangedFlow.getStatus());
        assertEquals(1L, flowRepository.count());
        assertEquals(0L, nodeRepository.count());
        assertEquals(0L, linkRepository.count());
    }

    @Test
    @DisplayName("최초 Flow 생성 시 INACTIVE로 저장 테스트")
    void firstFlowIsInactiveTest() {
        FlowCreateRequest request = FlowCreateRequest.builder()
                .locationId(10L)
                .name("최초 플로우 생성")
                .description("설명")
                .nodes(createValidNodes())
                .links(createValidLinks())
                .build();
        FlowResponse response = flowService.create(1L, 100L, request);
        entityManager.flush();
        entityManager.clear();
        Flow savedFlow = flowRepository.findById(response.flowId()).orElseThrow();
        assertEquals(FlowStatus.INACTIVE, savedFlow.getStatus());
        assertEquals(2, nodeRepository.findByFlowId(response.flowId()).size());
        assertEquals(1, linkRepository.findByFlowId(response.flowId()).size());
    }

    @Test
    @DisplayName("최초 생성 검증 실패시 Flow, Node, Link 모두 반환 테스트")
    void failedFirstFlowCreationTest() {
        FlowCreateRequest request = FlowCreateRequest
                .builder()
                .locationId(1L)
                .name("잘못된 플로우")
                .description("설명")
                .nodes(List.of(
                        FlowNodeRequest.builder()
                                .clientNodeKey("same")
                                .nodeType(NodeType.SENSOR)
                                .configuration(JsonNodeFactory.instance.objectNode())
                                .build(),
                        FlowNodeRequest.builder()
                                .clientNodeKey("same")
                                .nodeType(NodeType.ALERT)
                                .configuration(JsonNodeFactory.instance.objectNode())
                                .build()))
                .links(List.of(FlowLinkRequest.builder()
                        .sourceClientNodeKey("same")
                        .targetClientNodeKey("same")
                        .sourcePort("out")
                        .targetPort("in")
                        .build()))
                .build();
        assertThrows(InvalidFlowStructureException.class, () ->
                flowService.create(1L, 100L, request));
        entityManager.flush();
        entityManager.clear();
        assertEquals(0L, flowRepository.count());
        assertEquals(0L, nodeRepository.count());
        assertEquals(0L, linkRepository.count());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Node 저장 중 DB 예외 발생 시 이미 저장된 Flow 행까지 원자적으로 롤백된다")
    void dbRollbackTest() {
        // 1. 정상적인 사전 구성 Request 생성
        FlowCreateRequest request = FlowTestData.createTemperatureThreshold30FlowRequest(10L);

        // 2. Node 저장 시점에 DB 저장 실패(예외) 강제 발생 설정 (표준 Mockito doThrow 구문)
        doThrow(new RuntimeException("Node 저장 중 DB 오류 발생"))
                .when(nodeRepository).save(any());

        // 3. create 실행 시 예외 발생 검증
        assertThrows(RuntimeException.class, () -> flowService.create(1L, 100L, request));

        // 4. PostgreSQL DB에 Flow, Node, Link 모두 0건으로 원자적 롤백되었는지 검증
        assertEquals(0L, flowRepository.count());
        assertEquals(0L, nodeRepository.count());
        assertEquals(0L, linkRepository.count());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("수정 링크 저장 실패시 이전 상태로 롤백합니다.")
    void updateLinkRollbackTest() {
        Flow currentFlow = flowRepository.saveAndFlush(
                new Flow(1L, 10L, "온도 경고 v1", null, FlowStatus.ACTIVE)
        );
        Long currentFlowId = currentFlow.getId();
        Node oldSensor = nodeRepository.save(
                new Node(
                        currentFlowId,
                        NodeType.SENSOR,
                        JsonNodeFactory.instance.objectNode().put("devName", "old-sensor")
                )
        );
        Node oldAlert = nodeRepository.save(
                new Node(
                        currentFlowId,
                        NodeType.ALERT,
                        JsonNodeFactory.instance.objectNode()
                                .put("title", "이전 온도 경고")
                                .put("severity", "WARNING")
                                .put("message", "이전 경고 메시지")
                )
        );
        nodeRepository.flush();
        linkRepository.saveAndFlush(
                new Link(currentFlowId, oldSensor.getId(), "out", oldAlert.getId(), "in")
        );
        doThrow(new IllegalStateException("Link 저장 실패"))
                .when(linkRepository).save(any(Link.class));
        FlowUpdateRequest updateRequest = FlowTestData.createValidUpdateRequest("온도 경고 v2", "수정 설명");
        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> flowService.update(
                            1L,
                            100L,
                            currentFlowId,
                            updateRequest
                    )
            );
            Flow updatedFlow = flowRepository.findById(currentFlowId).orElseThrow();
            assertEquals(1L, flowRepository.count());
            assertEquals(FlowStatus.ACTIVE, updatedFlow.getStatus());
            assertEquals(2, nodeRepository.findByFlowId(currentFlowId).size());
            assertEquals(1, linkRepository.findByFlowId(currentFlowId).size());
            assertFalse(flowRepository.existsByGroupIdAndLocationIdAndName(
                    1L,
                    10L,
                    "온도 경고 v2"
            ));
        } finally {
            linkRepository.deleteAll();
            nodeRepository.deleteAll();
            flowRepository.deleteAll();
        }
    }
}
