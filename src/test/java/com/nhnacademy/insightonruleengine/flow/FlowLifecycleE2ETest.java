package com.nhnacademy.insightonruleengine.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.flow.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.cache.ActiveFlowDefinitionProvider;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.Link;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.dto.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.dto.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowUpdateRequest;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.repository.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.repository.NodeRepository;
import com.nhnacademy.insightonruleengine.flow.service.FlowService;
import com.nhnacademy.insightonruleengine.flow.validation.FlowLinkValidator;
import com.nhnacademy.insightonruleengine.flow.validation.FlowNodeValidator;
import com.nhnacademy.insightonruleengine.flow.validation.FlowPathValidator;
import com.nhnacademy.insightonruleengine.flow.validation.FlowStructureValidator;
import com.nhnacademy.insightonruleengine.flow.validation.LinkValidator;
import com.nhnacademy.insightonruleengine.flow.validation.NodeValidator;
import com.nhnacademy.insightonruleengine.flow.validation.FlowActivationValidator;
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
class FlowLifecycleE2ETest {

    private static final Long GROUP_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final Long LOCATION_ID = 10L;

    @Autowired
    private FlowService flowService;

    @Autowired
    private FlowDefinitionAssembler flowDefinitionAssembler;

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
    @DisplayName("최초 생성(INACTIVE) -> 모델 조립 -> 활성화(ACTIVE) -> 수정본 저장(v2) -> 영구 삭제까지 전 라이프사이클 E2E 검증")
    void fullFlowLifecycleE2ETest() {
        when(flowActivationValidator.validate(any())).thenReturn(List.of());

        // Step 1: 최초 Flow 생성 요청 (온도 30도 경보 플로우: Sensor -> Threshold 30 -> Alert)
        FlowCreateRequest createRequest = FlowTestData.createTemperatureThreshold30FlowRequest(LOCATION_ID);
        FlowResponse createResponse = flowService.create(GROUP_ID, USER_ID, createRequest);

        Long v1FlowId = createResponse.flowId();
        assertNotNull(v1FlowId);
        assertEquals(FlowStatus.INACTIVE, createResponse.status());

        // DB 저장 상태 직접 검증 (Flow 1건, Node 3건, Link 2건)
        Flow v1Flow = flowRepository.findById(v1FlowId).orElseThrow();
        assertEquals(FlowStatus.INACTIVE, v1Flow.getStatus());

        List<Node> v1Nodes = nodeRepository.findByFlowId(v1FlowId);
        assertEquals(3, v1Nodes.size());

        List<Link> v1Links = linkRepository.findByFlowId(v1FlowId);
        assertEquals(2, v1Links.size());

        // Step 2: FlowDefinitionAssembler를 통한 저장된 플로우 실행 모델 조립 검증
        FlowDefinition v1Definition = flowDefinitionAssembler.assemble(GROUP_ID, v1FlowId);
        assertEquals(v1FlowId, v1Definition.flowId());
        assertEquals(FlowStatus.INACTIVE, v1Definition.status());
        assertEquals(3, v1Definition.nodes().size());
        assertEquals(2, v1Definition.links().size());

        // Step 3: 상태 변경 (INACTIVE -> ACTIVE 활성화)
        FlowResponse activeResponse = flowService.changeActivationStatus(
                GROUP_ID, USER_ID, v1FlowId, new FlowStatusChangeRequest(FlowStatus.ACTIVE)
        );
        assertEquals(FlowStatus.ACTIVE, activeResponse.status());

        FlowDefinition activeDefinition = flowDefinitionAssembler.assembleActive(GROUP_ID, v1FlowId);
        assertEquals(FlowStatus.ACTIVE, activeDefinition.status());

        // Step 4: Flow 수정본 저장 (v1은 ARCHIVED 처리되고 v2 신규 Flow가 INACTIVE로 생성)
        FlowUpdateRequest updateRequest = FlowTestData.createValidUpdateRequest("온도 경고 v2", "수정된 설명");
        FlowResponse v2Response = flowService.update(GROUP_ID, USER_ID, v1FlowId, updateRequest);

        Long v2FlowId = v2Response.flowId();
        assertNotNull(v2FlowId);
        assertTrue(!v2FlowId.equals(v1FlowId), "수정 시 새 flowId가 발급되어야 함");
        assertEquals(FlowStatus.INACTIVE, v2Response.status());

        // DB 이력 보존 검증: 이전 v1 Flow는 ARCHIVED 상태로 유지되고 노드/링크도 보존됨
        Flow archivedV1Flow = flowRepository.findById(v1FlowId).orElseThrow();
        assertEquals(FlowStatus.ARCHIVED, archivedV1Flow.getStatus());
        assertEquals(3, nodeRepository.findByFlowId(v1FlowId).size());
        assertEquals(2, linkRepository.findByFlowId(v1FlowId).size());

        // 새 v2 Flow는 INACTIVE 상태로 신규 노드/링크를 가짐 (createValidNodes: 2개, createValidLinks: 1개)
        Flow v2Flow = flowRepository.findById(v2FlowId).orElseThrow();
        assertEquals(FlowStatus.INACTIVE, v2Flow.getStatus());
        assertEquals(2, nodeRepository.findByFlowId(v2FlowId).size());
        assertEquals(1, linkRepository.findByFlowId(v2FlowId).size());

        // Step 5: 보관 및 영구 삭제 (v2 플로우 보관 후 영구 삭제)
        flowService.archive(GROUP_ID, USER_ID, v2FlowId);
        assertEquals(FlowStatus.ARCHIVED, flowRepository.findById(v2FlowId).orElseThrow().getStatus());

        flowService.delete(GROUP_ID, USER_ID, v2FlowId);
        entityManager.flush();
        entityManager.clear();

        // v2 Flow와 해당 노드/링크만 완전히 영구 삭제되었는지 확인
        assertTrue(flowRepository.findById(v2FlowId).isEmpty());
        assertTrue(nodeRepository.findByFlowId(v2FlowId).isEmpty());
        assertTrue(linkRepository.findByFlowId(v2FlowId).isEmpty());

        // 이력으로 보관된 이전 v1 Flow는 DB에 그대로 남아있는지 확인
        assertTrue(flowRepository.findById(v1FlowId).isPresent());
        assertEquals(3, nodeRepository.findByFlowId(v1FlowId).size());
        assertEquals(2, linkRepository.findByFlowId(v1FlowId).size());
    }
}
