package com.nhnacademy.insightonruleengine.flow.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.client.core.CoreActuatorClient;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowUpdateRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.response.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.application.assembly.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.application.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowActivationValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowLinkValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowNodeValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowPathValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowStructureValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.LinkValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.NodeConfigurationValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.NodeValidator;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.InvalidFlowStructureException;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.NodeRepository;
import com.nhnacademy.insightonruleengine.runner.application.schedule.ScheduleFlowScheduler;
import com.nhnacademy.insightonruleengine.runner.infrastructure.cache.ActiveFlowDefinitionProvider;
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

    @Autowired
    private FlowDefinitionAssembler flowDefinitionAssembler;

    @MockitoBean
    private GroupAuthorizationService groupAuthorizationService;

    @MockitoBean
    private FlowActivationValidator flowActivationValidator;

    @MockitoBean
    private NodeConfigurationValidator nodeConfigurationValidator;

    @MockitoBean
    private ActiveFlowDefinitionProvider activeFlowDefinitionProvider;

    @MockitoBean
    private ScheduleFlowScheduler scheduleFlowScheduler;

    @MockitoBean
    private CoreActuatorClient coreActuatorClient;

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

        assertNotEquals(currentFlowId, response.flowId());
        assertEquals(FlowStatus.ARCHIVED, archivedFlow.getStatus());
        assertEquals(FlowStatus.INACTIVE, updatedFlow.getStatus());
        assertEquals(10L, updatedFlow.getLocationId());
        assertEquals("온도 경고 v2", updatedFlow.getName());
        assertEquals(2, nodeRepository.findByFlowId(response.flowId()).size());
        assertEquals(1, linkRepository.findByFlowId(response.flowId()).size());
    }

    @Test
    @DisplayName("Action fan-out Flow를 저장하고 활성화 가능한 Definition으로 조립한다")
    void createAndActivateActionFanOutFlow() {
        FlowResponse created = flowService.create(1L, 100L, actionFanOutCreateRequest());
        entityManager.flush();
        entityManager.clear();

        FlowResponse activated = flowService.changeActivationStatus(
                1L,
                100L,
                created.flowId(),
                FlowStatusChangeRequest.builder().status(FlowStatus.ACTIVE).build()
        );
        FlowDefinition definition = flowDefinitionAssembler.assemble(1L, created.flowId());

        assertEquals(FlowStatus.ACTIVE, activated.status());
        assertEquals(3, definition.nodes().size());
        assertEquals(2, definition.links().size());
        assertEquals(definition.links().get(0).sourceNodeId(), definition.links().get(1).sourceNodeId());
        assertEquals(definition.links().get(0).sourcePort(), definition.links().get(1).sourcePort());
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
        FlowUpdateRequest duplicateNameRequest = updateRequest("온도 경고 v2", null);

        assertThrows(
                DuplicateFlowNameException.class,
                () -> flowService.update(1L, 100L, currentFlowId, duplicateNameRequest)
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

    private FlowCreateRequest actionFanOutCreateRequest() {
        return FlowCreateRequest.builder()
                .locationId(10L)
                .name("다중 Action Flow")
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
                                .build(),
                        FlowNodeRequest.builder()
                                .clientNodeKey("actuator")
                                .nodeType(NodeType.ACTUATOR_CONTROL)
                                .configuration(JsonNodeFactory.instance.objectNode())
                                .build()
                ))
                .links(List.of(
                        FlowLinkRequest.builder()
                                .sourceClientNodeKey("sensor")
                                .targetClientNodeKey("alert")
                                .sourcePort("out")
                                .targetPort("in")
                                .build(),
                        FlowLinkRequest.builder()
                                .sourceClientNodeKey("sensor")
                                .targetClientNodeKey("actuator")
                                .sourcePort("out")
                                .targetPort("in")
                                .build()
                ))
                .build();
    }
}
