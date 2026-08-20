package com.nhnacademy.insightonruleengine.flow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.cache.ActiveFlowDefinitionProvider;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.dto.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.dto.FlowUpdateRequest;
import com.nhnacademy.insightonruleengine.flow.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.exception.InvalidFlowStructureException;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.repository.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.repository.NodeRepository;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.validation.FlowActivationValidator;
import com.nhnacademy.insightonruleengine.flow.validation.FlowLinkValidator;
import com.nhnacademy.insightonruleengine.flow.validation.FlowNodeValidator;
import com.nhnacademy.insightonruleengine.flow.validation.FlowPathValidator;
import com.nhnacademy.insightonruleengine.flow.validation.FlowStructureValidator;
import com.nhnacademy.insightonruleengine.flow.validation.LinkValidator;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        FlowService.class,
        FlowDefinitionAssembler.class,
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

    @Autowired
    private NodeRepository nodeRepository;

    @Autowired
    private LinkRepository linkRepository;

    @MockitoBean
    private GroupAuthorizationService groupAuthorizationService;

    @MockitoBean
    private FlowActivationValidator flowActivationValidator;

    @MockitoBean
    private ActiveFlowDefinitionProvider activeFlowDefinitionProvider;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Flow 수정은 기존 Flow를 보관하고 새 ID의 INACTIVE Flow를 저장한다")
    void updateCreatesNewFlowId() {
        Flow currentFlow = flowRepository.saveAndFlush(
                new Flow(1L, 10L, "온도 경고 v1", null, FlowStatus.ACTIVE)
        );
        Long currentFlowId = currentFlow.getId();

        FlowResponse response = flowService.update(
                1L,
                100L,
                currentFlowId,
                updateRequest(" 온도 경고 v2 ", "수정 설명")
        );
        entityManager.flush();
        entityManager.clear();

        Flow archivedFlow = flowRepository.findById(currentFlowId).orElseThrow();
        Flow updatedFlow = flowRepository.findById(response.flowId()).orElseThrow();

        assertFalse(currentFlowId.equals(response.flowId()));
        assertEquals(FlowStatus.ARCHIVED, archivedFlow.getStatus());
        assertEquals(FlowStatus.INACTIVE, updatedFlow.getStatus());
        assertEquals(10L, updatedFlow.getLocationId());
        assertEquals("온도 경고 v2", updatedFlow.getName());
        assertEquals(2, nodeRepository.findByFlowId(response.flowId()).size());
        assertEquals(1, linkRepository.findByFlowId(response.flowId()).size());
    }

    @Test
    @DisplayName("Flow 복구는 새 행 없이 기존 ID를 INACTIVE로 전환한다")
    void restoreKeepsExistingFlowId() {
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
    void failedUpdateKeepsCurrentFlow() {
        Flow currentFlow = flowRepository.save(
                new Flow(1L, 10L, "온도 경고 v1", null, FlowStatus.ACTIVE)
        );
        flowRepository.saveAndFlush(
                new Flow(1L, 10L, "온도 경고 v2", null, FlowStatus.ARCHIVED)
        );
        Long currentFlowId = currentFlow.getId();

        assertThrows(
                DuplicateFlowNameException.class,
                () -> flowService.update(
                        1L,
                        100L,
                        currentFlowId,
                        updateRequest("온도 경고 v2", null)
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
    void invalidStructurePreventsPersistenceTest() {
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

    private FlowUpdateRequest updateRequest(String name, String description) {
        return FlowUpdateRequest.builder()
                .name(name)
                .description(description)
                .nodes(List.of(
                        FlowNodeRequest.builder()
                                .clientNodeKey("sensor")
                                .nodeType(NodeType.SENSOR)
                                .configuration(JsonNodeFactory.instance.objectNode())
                                .build(),
                        FlowNodeRequest.builder()
                                .clientNodeKey("alert")
                                .nodeType(NodeType.ALERT)
                                .configuration(JsonNodeFactory.instance.objectNode())
                                .build()))
                .links(List.of(FlowLinkRequest.builder()
                        .sourceClientNodeKey("sensor")
                        .targetClientNodeKey("alert")
                        .sourcePort("out")
                        .targetPort("in")
                        .build()))
                .build();
    }
}
