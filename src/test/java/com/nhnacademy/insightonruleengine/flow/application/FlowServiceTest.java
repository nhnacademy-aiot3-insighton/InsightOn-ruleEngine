package com.nhnacademy.insightonruleengine.flow.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.client.core.CoreActuatorClient;
import com.nhnacademy.insightonruleengine.client.core.LocationResponse;
import com.nhnacademy.insightonruleengine.flow.application.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.application.authorization.GroupRole;
import com.nhnacademy.insightonruleengine.runner.infrastructure.cache.ActiveFlowDefinitionProvider;
import com.nhnacademy.insightonruleengine.runner.application.schedule.ScheduleFlowScheduler;
import com.nhnacademy.insightonruleengine.flow.FlowTestData;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.Link;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.application.assembly.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.response.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowUpdateRequest;
import com.nhnacademy.insightonruleengine.flow.domain.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.FlowDeletionNotAllowedException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.FlowNotFoundException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.ForbiddenException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.InvalidAiDraftNameException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.InvalidFlowStatusTransitionException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.InvalidFlowStructureException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.LocationNotFoundException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.ReservedFlowNamePrefixException;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.NodeRepository;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowStructureValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowActivationValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.NodeConfigurationValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureErrorCode;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.NodeErrorCode;
import feign.FeignException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class FlowServiceTest {

    private static final long GROUP_ID = 1L;
    private static final long USER_ID = 100L;

    @Mock
    FlowRepository flowRepository;

    @Mock
    GroupAuthorizationService groupAuthorizationService;

    @Mock
    NodeRepository nodeRepository;

    @Mock
    LinkRepository linkRepository;

    @Mock
    FlowStructureValidator flowStructureValidator;

    @Mock
    NodeConfigurationValidator nodeConfigurationValidator;

    @Mock
    FlowDefinitionAssembler flowDefinitionAssembler;

    @Mock
    FlowActivationValidator flowActivationValidator;

    @Mock
    ActiveFlowDefinitionProvider activeFlowDefinitionProvider;

    @Mock
    ScheduleFlowScheduler scheduleFlowScheduler;

    @Mock
    CoreActuatorClient coreActuatorClient;

    @InjectMocks
    FlowService flowService;

    @Test
    @DisplayName("INACTIVE 상태 플로우 생성 테스트")
    void createInactiveFlowTest() {
        FlowCreateRequest flowCreateRequest = FlowCreateRequest.builder()
                .locationId(2L)
                .name(" 온도 알람 ")
                .description("온도가 너무 높아요")
                .nodes(List.of())
                .links(List.of())
                .build();
        when(flowRepository.existsByGroupIdAndLocationIdAndName(
                1L,
                2L,
                "온도 알람")).thenReturn(false);
        when(flowRepository.save(any(Flow.class))).thenAnswer(flow -> flow.getArgument(0));

        FlowResponse flowResponse = flowService.create(GROUP_ID, USER_ID, flowCreateRequest);
        verify(groupAuthorizationService).requireRole(GROUP_ID, USER_ID, GroupRole.MANAGER);
        verify(flowRepository).save(any(Flow.class));
        Assertions.assertEquals(1L, flowResponse.groupId());
        Assertions.assertEquals(2L, flowResponse.locationId());
        Assertions.assertEquals("온도 알람", flowResponse.name());
        Assertions.assertEquals("온도가 너무 높아요", flowResponse.description());
        Assertions.assertEquals(FlowStatus.INACTIVE, flowResponse.status());

    }

    @Test
    @DisplayName("유저가 이름에 [AI] 접두어를 붙이면 생성을 거부한다")
    void createRejectsAiDraftPrefixTest() {
        FlowCreateRequest request = FlowCreateRequest.builder()
                .locationId(2L)
                .name("[AI] 위장한 이름")
                .description(null)
                .nodes(List.of())
                .links(List.of())
                .build();

        assertThrows(
                ReservedFlowNamePrefixException.class,
                () -> flowService.create(GROUP_ID, USER_ID, request));

        verifyNoInteractions(flowRepository);
    }

    @Test
    @DisplayName("Flow 생성 시 요청의 Node와 Link를 함께 저장한다")
    void createFlowGraphTest() {
        FlowCreateRequest request = FlowCreateRequest.builder()
                .locationId(2L)
                .name("온도 알람")
                .description("온도 알람 그래프")
                .nodes(FlowTestData.createValidNodes())
                .links(FlowTestData.createValidLinks())
                .build();
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeRepository.save(any(Node.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));

        flowService.create(GROUP_ID, USER_ID, request);

        verify(flowRepository).save(any(Flow.class));
        verify(nodeRepository, times(2)).save(any(Node.class));
        verify(linkRepository).save(any(Link.class));
    }

    @Test
    @DisplayName("동일 플로우 이름 중복 테스트")
    void sameFlowNameTest() {
        FlowCreateRequest flowCreateRequest = FlowCreateRequest.builder()
                .locationId(1L)
                .name("온도")
                .description("온도")
                .nodes(List.of())
                .links(List.of())
                .build();
        when(flowRepository.existsByGroupIdAndLocationIdAndName(
                1L,
                1L,
                "온도")).thenReturn(true);
        Assertions.assertThrows(DuplicateFlowNameException.class, () -> flowService.create(
                GROUP_ID,
                USER_ID,
                flowCreateRequest));
    }

    // 쓰기 권한이 거부되면 Flow 저장 로직이 시작되지 않도록 확인합니다.
    @Test
    @DisplayName("MEMBER의 생성 요청이 거부되면 Repository를 호출하지 않는다")
    void repositoryTest() {
        FlowCreateRequest request = FlowCreateRequest.builder()
                .locationId(1L)
                .name("온도")
                .description(null)
                .nodes(List.of())
                .links(List.of())
                .build();
        ForbiddenException exception = new ForbiddenException("MANAGER 이상 권한이 필요합니다.");
        doThrow(exception)
                .when(groupAuthorizationService)
                .requireRole(GROUP_ID, USER_ID, GroupRole.MANAGER);

        assertThrows(
                ForbiddenException.class,
                () -> flowService.create(GROUP_ID, USER_ID, request));

        verifyNoInteractions(flowRepository);
    }

    @Test
    @DisplayName("기본 목록은 ARCHIVED를 제외하는 Repository 조건을 사용한다")
    void findAllExceptArchivedTest() {
        Flow activeFlow = new Flow(1L, 1L, "활성 Flow", null, FlowStatus.ACTIVE);
        when(flowRepository.findAllByGroupIdAndStatusNot(1L, FlowStatus.ARCHIVED))
                .thenReturn(List.of(activeFlow));

        List<FlowResponse> responses = flowService.findAllUnarchivedFlows(GROUP_ID, USER_ID);

        Assertions.assertEquals(1, responses.size());
        Assertions.assertEquals(FlowStatus.ACTIVE, responses.getFirst().status());
        verify(groupAuthorizationService).requireRole(GROUP_ID, USER_ID, GroupRole.MEMBER);
        verify(flowRepository).findAllByGroupIdAndStatusNot(1L, FlowStatus.ARCHIVED);
    }

    // 상태 조건 목록도 조회 전에 MEMBER 권한을 한 번 확인하도록 고정합니다.
    @Test
    @DisplayName("상태별 목록 조회는 MEMBER 최소 역할을 확인한다")
    void findListMemberTest() {
        when(flowRepository.findAllByGroupIdAndStatus(GROUP_ID, FlowStatus.ACTIVE))
                .thenReturn(List.of());

        flowService.findByGroupIdAndStatus(GROUP_ID, USER_ID, FlowStatus.ACTIVE);

        verify(groupAuthorizationService).requireRole(GROUP_ID, USER_ID, GroupRole.MEMBER);
    }

    // 장소·상태 조건 목록도 다른 목록 요청과 같은 읽기 권한을 사용하도록 확인합니다.
    @Test
    @DisplayName("장소·상태별 목록 조회는 MEMBER 최소 역할을 확인한다")
    void locationListMemberTest() {
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(
                GROUP_ID,
                10L,
                FlowStatus.ACTIVE)).thenReturn(List.of());

        flowService.findByGroupIdAndLocationIdAndStatus(
                GROUP_ID,
                USER_ID,
                10L,
                FlowStatus.ACTIVE);

        verify(groupAuthorizationService).requireRole(GROUP_ID, USER_ID, GroupRole.MEMBER);
    }

    @Test
    @DisplayName("다른 그룹의 Flow는 존재하지 않는 Flow와 같은 예외로 처리한다")
    void rejectOtherGroupFlowTest() {
        Flow flow = new Flow(2L, 1L, "다른 그룹 Flow", null, FlowStatus.INACTIVE);
        when(flowRepository.findById(1L)).thenReturn(Optional.of(flow));

        assertThrows(FlowNotFoundException.class, () -> flowService.findById(GROUP_ID, USER_ID, 1L));
        verify(groupAuthorizationService).requireRole(GROUP_ID, USER_ID, GroupRole.MEMBER);
    }

    @Test
    @DisplayName("ACTIVE ↔ INACTIVE 상태 전환 테스트")
    void changeStatusTest() {
        Flow flow = new Flow(1L, 1L, "기존 Flow", "기존 설명", FlowStatus.ACTIVE);
        when(flowRepository.findById(1L)).thenReturn(Optional.of(flow));
        FlowStatusChangeRequest request = new FlowStatusChangeRequest(FlowStatus.INACTIVE);

        FlowResponse response = flowService.changeActivationStatus(GROUP_ID, USER_ID, 1L, request);

        Assertions.assertEquals(FlowStatus.INACTIVE, flow.getStatus());
        Assertions.assertEquals(FlowStatus.INACTIVE, response.status());

        FlowStatusChangeRequest activeRequest = new FlowStatusChangeRequest(FlowStatus.ACTIVE);

        FlowResponse activeResponse =
                flowService.changeActivationStatus(GROUP_ID, USER_ID, 1L, activeRequest);

        Assertions.assertEquals(FlowStatus.ACTIVE, flow.getStatus());
        Assertions.assertEquals(FlowStatus.ACTIVE, activeResponse.status());
        verify(groupAuthorizationService, times(2))
                .requireRole(GROUP_ID, USER_ID, GroupRole.MANAGER);

    }

    @Test
    @DisplayName("Flow 수정 시 기존 Flow를 보관하고 새 Flow를 INACTIVE로 생성한다")
    void updateFlowInactiveTest() {
        Flow currentFlow = new Flow(1L, 1L, "기존 Flow", "기존 설명", FlowStatus.ACTIVE);
        FlowUpdateRequest request = updateRequest("수정 Flow", "수정 설명");
        when(flowRepository.findById(1L)).thenReturn(Optional.of(currentFlow));
        when(flowRepository.existsByGroupIdAndLocationIdAndName(
                1L,
                1L,
                "수정 Flow")).thenReturn(false);
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeRepository.save(any(Node.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FlowResponse response = flowService.update(GROUP_ID, USER_ID, 1L, request);

        Assertions.assertEquals(FlowStatus.ARCHIVED, currentFlow.getStatus());
        Assertions.assertEquals(FlowStatus.INACTIVE, response.status());
        verify(groupAuthorizationService).requireRole(GROUP_ID, USER_ID, GroupRole.MANAGER);
        verify(nodeRepository, times(2)).save(any(Node.class));
        verify(linkRepository).save(any(Link.class));
    }

    // rejectAiDraftPrefix()가 getFlow() 조회보다 먼저 실행돼 Repository를 아예 건드리지 않습니다.
    @Test
    @DisplayName("유저가 수정 이름에 [AI] 접두어를 붙이면 수정을 거부한다")
    void updateRejectsAiDraftPrefixTest() {
        FlowUpdateRequest request = updateRequest("[AI] 위장한 이름", "수정 설명");

        assertThrows(
                ReservedFlowNamePrefixException.class,
                () -> flowService.update(GROUP_ID, USER_ID, 1L, request));

        verifyNoInteractions(flowRepository);
    }

    // 실행 중인 Flow도 권한을 확인한 뒤 안전하게 휴지통으로 보내는지 확인합니다.
    @Test
    @DisplayName("ACTIVE Flow를 휴지통의 ARCHIVED 상태로 변경한다")
    void archiveActiveFlowTest() {
        Flow activeFlow = new Flow(GROUP_ID, 1L, "현재 Flow", null, FlowStatus.ACTIVE);
        when(flowRepository.findById(1L)).thenReturn(Optional.of(activeFlow));

        FlowResponse response = flowService.archive(GROUP_ID, USER_ID, 1L);

        Assertions.assertEquals(FlowStatus.ARCHIVED, activeFlow.getStatus());
        Assertions.assertEquals(FlowStatus.ARCHIVED, response.status());
        verify(groupAuthorizationService).requireRole(GROUP_ID, USER_ID, GroupRole.MANAGER);
    }

    // 실행 대기 중인 Flow를 별도 삭제 없이 휴지통으로 보내는지 확인합니다.
    @Test
    @DisplayName("INACTIVE Flow를 휴지통의 ARCHIVED 상태로 변경한다")
    void archiveInactiveFlowTest() {
        Flow inactiveFlow = new Flow(GROUP_ID, 1L, "현재 Flow", null, FlowStatus.INACTIVE);
        when(flowRepository.findById(1L)).thenReturn(Optional.of(inactiveFlow));

        FlowResponse response = flowService.archive(GROUP_ID, USER_ID, 1L);

        Assertions.assertEquals(FlowStatus.ARCHIVED, inactiveFlow.getStatus());
        Assertions.assertEquals(FlowStatus.ARCHIVED, response.status());
    }

    @Test
    @DisplayName("수정 이름 검증이 실패하면 기존 Flow 상태를 유지한다")
    void ValidationFailsTest() {
        Flow currentFlow = new Flow(1L, 1L, "기존 Flow", null, FlowStatus.ACTIVE);
        FlowUpdateRequest request = updateRequest("중복 Flow", null);
        when(flowRepository.findById(1L)).thenReturn(Optional.of(currentFlow));
        when(flowRepository.existsByGroupIdAndLocationIdAndName(1L, 1L, "중복 Flow"))
                .thenReturn(true);

        assertThrows(
                DuplicateFlowNameException.class,
                () -> flowService.update(GROUP_ID, USER_ID, 1L, request));

        Assertions.assertEquals(FlowStatus.ACTIVE, currentFlow.getStatus());
        verify(flowRepository, never()).save(any(Flow.class));
    }

    @Test
    @DisplayName("중복 clientNodeKey가 있으면 수정 Flow를 저장하지 않는다")
    void duplicateClientNodeKeyTest() {
        Flow currentFlow = new Flow(1L, 1L, "기존 Flow", null, FlowStatus.ACTIVE);
        FlowUpdateRequest request = FlowUpdateRequest.builder()
                .name("수정 Flow")
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
        when(flowRepository.findById(1L)).thenReturn(Optional.of(currentFlow));
        when(flowStructureValidator.validate(request.nodes(), request.links()))
                .thenReturn(List.of(new FlowStructureValidationError(
                        NodeErrorCode.DUPLICATE_CLIENT_NODE_KEY,
                        "same",
                        "nodes[1].clientNodeKey",
                        "clientKey는 중복될 수 없습니다.")));

        assertThrows(
                InvalidFlowStructureException.class,
                () -> flowService.update(GROUP_ID, USER_ID, 1L, request));

        Assertions.assertEquals(FlowStatus.ACTIVE, currentFlow.getStatus());
        verify(flowRepository, never()).save(any(Flow.class));
        verifyNoInteractions(nodeRepository, linkRepository);
    }

    @Test
    @DisplayName("노드 설정 검증이 실패하면 Flow를 저장하지 않는다")
    void invalidNodeConfigurationTest() {
        FlowCreateRequest request = FlowTestData.createScheduledActuatorFlowRequest(2L);
        when(nodeConfigurationValidator.validate(request.nodes()))
                .thenReturn(List.of(new FlowStructureValidationError(
                        NodeErrorCode.INVALID_NODE_CONFIGURATION,
                        "hourly_schedule",
                        "nodes[0].configuration.cron",
                        "cron 표현식은 6자리이며 초 필드는 0이어야 합니다.")));

        assertThrows(
                InvalidFlowStructureException.class,
                () -> flowService.create(GROUP_ID, USER_ID, request));

        verify(flowRepository, never()).save(any(Flow.class));
        verifyNoInteractions(nodeRepository, linkRepository);
    }

    // 지원하지 않는 입력 포트가 규칙 기반 플로우 구성에 저장되지 않도록 확인합니다.
    @Test
    @DisplayName("Target Port가 in이 아니면 수정 Flow를 저장하지 않는다")
    void invalidTargetPortTest() {
        Flow currentFlow = new Flow(1L, 1L, "기존 Flow", null, FlowStatus.ACTIVE);
        FlowUpdateRequest request = FlowUpdateRequest.builder()
                .name("수정 Flow")
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
                        .targetPort("unsupported")
                        .build()))
                .build();
        when(flowRepository.findById(1L)).thenReturn(Optional.of(currentFlow));
        when(flowStructureValidator.validate(request.nodes(), request.links()))
                .thenReturn(List.of(new FlowStructureValidationError(
                        FlowStructureErrorCode.INVALID_PORT,
                        "alert",
                        "links[0].targetPort",
                        "타겟 포트는 in만 사용할 수 있습니다.")));

        assertThrows(
                InvalidFlowStructureException.class,
                () -> flowService.update(GROUP_ID, USER_ID, 1L, request));

        Assertions.assertEquals(FlowStatus.ACTIVE, currentFlow.getStatus());
        verify(flowRepository, never()).save(any(Flow.class));
        verifyNoInteractions(nodeRepository, linkRepository);
    }

    @Test
    @DisplayName("Link 저장 실패 시 기존 Flow를 보관 상태로 바꾸지 않는다")
    void linkSaveFailureTest() {
        Flow currentFlow = new Flow(1L, 1L, "기존 Flow", null, FlowStatus.ACTIVE);
        FlowUpdateRequest request = updateRequest("수정 Flow", null);
        when(flowRepository.findById(1L)).thenReturn(Optional.of(currentFlow));
        when(flowRepository.existsByGroupIdAndLocationIdAndName(1L, 1L, "수정 Flow"))
                .thenReturn(false);
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeRepository.save(any(Node.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("link save failed"))
                .when(linkRepository)
                .save(any(Link.class));

        assertThrows(
                IllegalStateException.class,
                () -> flowService.update(GROUP_ID, USER_ID, 1L, request));

        Assertions.assertEquals(FlowStatus.ACTIVE, currentFlow.getStatus());
    }

    @Test
    @DisplayName("ARCHIVED Flow를 기존 ID 그대로 INACTIVE로 복구한다")
    void restoreArchivedFlowInactiveTest() {
        Flow archivedFlow = new Flow(1L, 1L, "이전 Flow", null, FlowStatus.ARCHIVED);
        when(flowRepository.findById(2L)).thenReturn(Optional.of(archivedFlow));
        when(flowRepository.existsByGroupIdAndLocationIdAndNameAndStatusNot(1L, 1L, "이전 Flow", FlowStatus.ARCHIVED))
                .thenReturn(false);

        FlowResponse response = flowService.restore(GROUP_ID, USER_ID, 2L);

        Assertions.assertEquals(FlowStatus.INACTIVE, archivedFlow.getStatus());
        Assertions.assertEquals(FlowStatus.INACTIVE, response.status());
        verify(groupAuthorizationService).requireRole(GROUP_ID, USER_ID, GroupRole.MANAGER);
    }

    // archive된 동안(부분 유니크 인덱스가 ARCHIVED를 제외하므로) 같은 이름의 새 Flow가 만들어졌을 수
    // 있다. DB 제약이 이 충돌을 막아주지 않으므로 서비스 레벨에서 막아야 한다.
    @Test
    @DisplayName("복구 대상과 같은 이름의 살아있는 Flow가 있으면 복구를 거부한다")
    void rejectRestoreWhenNameAlreadyTakenTest() {
        Flow archivedFlow = new Flow(1L, 1L, "[AI] co2 예방 자동화", null, FlowStatus.ARCHIVED);
        when(flowRepository.findById(2L)).thenReturn(Optional.of(archivedFlow));
        when(flowRepository.existsByGroupIdAndLocationIdAndNameAndStatusNot(
                1L, 1L, "[AI] co2 예방 자동화", FlowStatus.ARCHIVED))
                .thenReturn(true);

        assertThrows(DuplicateFlowNameException.class, () -> flowService.restore(GROUP_ID, USER_ID, 2L));

        Assertions.assertEquals(FlowStatus.ARCHIVED, archivedFlow.getStatus());
    }

    @Test
    @DisplayName("ARCHIVED가 아닌 Flow는 복구할 수 없다")
    void rejectNonArchivedFlowRestoreTest() {
        Flow inactiveFlow = new Flow(1L, 1L, "현재 Flow", null, FlowStatus.INACTIVE);
        when(flowRepository.findById(2L)).thenReturn(Optional.of(inactiveFlow));
        when(flowRepository.existsByGroupIdAndLocationIdAndNameAndStatusNot(1L, 1L, "현재 Flow", FlowStatus.ARCHIVED))
                .thenReturn(false);

        assertThrows(
                InvalidFlowStatusTransitionException.class,
                () -> flowService.restore(GROUP_ID, USER_ID, 2L));

        Assertions.assertEquals(FlowStatus.INACTIVE, inactiveFlow.getStatus());
    }

    @Test
    @DisplayName("ARCHIVED Flow는 로드한 Entity를 사용해 삭제한다")
    void deleteArchivedFlowTest() {
        Flow archivedFlow = new Flow(1L, 1L, "보관 Flow", null, FlowStatus.ARCHIVED);
        when(flowRepository.findById(1L)).thenReturn(Optional.of(archivedFlow));

        flowService.delete(GROUP_ID, USER_ID, 1L);

        verify(groupAuthorizationService).requireRole(GROUP_ID, USER_ID, GroupRole.MANAGER);
        verify(flowRepository).delete(archivedFlow);
    }

    @Test
    @DisplayName("ARCHIVED가 아닌 Flow는 영구 삭제할 수 없다")
    void rejectActiveFlowDeleteTest() {
        Flow activeFlow = new Flow(1L, 1L, "활성 Flow", null, FlowStatus.ACTIVE);
        when(flowRepository.findById(1L)).thenReturn(Optional.of(activeFlow));

        assertThrows(
                FlowDeletionNotAllowedException.class,
                () -> flowService.delete(GROUP_ID, USER_ID, 1L));

        verify(flowRepository, never()).delete(any(Flow.class));
    }

    // ARCHIVED된 Flow는 부분 유니크 인덱스에서 이름을 점유하지 않으므로, 같은 이름의 ARCHIVED Flow만
    // 있어도 "살아있는" 기존 Flow가 없는 것과 동일하게 새로 만듭니다(더 이상 그대로 반환하지 않음).
    @Test
    @DisplayName("같은 이름의 ARCHIVED Flow만 있으면 새로 만든다")
    void createAiDraftCreatesNewWhenOnlyArchivedNameExistsTest() {
        FlowCreateRequest request = aiDraftRequest(2L);
        when(flowRepository.findByGroupIdAndLocationIdAndNameAndStatusNot(
                GROUP_ID, 2L, request.name(), FlowStatus.ARCHIVED))
                .thenReturn(Optional.empty());
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeRepository.save(any(Node.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coreActuatorClient.getLocation(2L))
                .thenReturn(new LocationResponse(2L, GROUP_ID, "회의실", LocationResponse.AutoControlMode.SUGGESTION));

        FlowResponse response = flowService.createAiDraft(GROUP_ID, request);

        Assertions.assertEquals(FlowStatus.INACTIVE, response.status());
        Assertions.assertNull(response.replacedFlowId());
        verify(flowRepository).save(any(Flow.class));
    }

    @Test
    @DisplayName("AI draft는 위치가 SUGGESTION 모드면 INACTIVE로 생성한다")
    void createAiDraftCreatesInactiveWhenSuggestionModeTest() {
        FlowCreateRequest request = aiDraftRequest(2L);
        when(flowRepository.findByGroupIdAndLocationIdAndNameAndStatusNot(
                GROUP_ID, 2L, request.name(), FlowStatus.ARCHIVED))
                .thenReturn(Optional.empty());
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeRepository.save(any(Node.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coreActuatorClient.getLocation(2L))
                .thenReturn(new LocationResponse(2L, GROUP_ID, "회의실", LocationResponse.AutoControlMode.SUGGESTION));

        FlowResponse response = flowService.createAiDraft(GROUP_ID, request);

        Assertions.assertEquals(FlowStatus.INACTIVE, response.status());
        verifyNoInteractions(activeFlowDefinitionProvider, scheduleFlowScheduler);
    }

    @Test
    @DisplayName("AI draft는 위치가 AI_DIRECT 모드고 실행 가능한 구조면 ACTIVE로 만들고 라우트·스케줄을 등록한다")
    void createAiDraftActivatesWhenAiDirectAndValidTest() {
        FlowCreateRequest request = aiDraftRequest(2L);
        when(flowRepository.findByGroupIdAndLocationIdAndNameAndStatusNot(
                GROUP_ID, 2L, request.name(), FlowStatus.ARCHIVED))
                .thenReturn(Optional.empty());
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeRepository.save(any(Node.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coreActuatorClient.getLocation(2L))
                .thenReturn(new LocationResponse(2L, GROUP_ID, "회의실", LocationResponse.AutoControlMode.AI_DIRECT));

        FlowResponse response = flowService.createAiDraft(GROUP_ID, request);

        Assertions.assertEquals(FlowStatus.ACTIVE, response.status());
        verify(activeFlowDefinitionProvider).refreshAfterCommit(GROUP_ID, 2L);
        verify(scheduleFlowScheduler).registerAfterCommit(eq(GROUP_ID), any());
    }

    // Core가 확인해준 위치가 실제로는 다른 그룹 소유라면, 그 조합으로 Flow가 만들어지면 안 됩니다
    // (테넌트 경계 보호). 저장 전에 걸러야 하므로 flowRepository.save()가 호출되면 안 됩니다.
    @Test
    @DisplayName("Core 응답의 groupId가 요청과 다르면 저장 전에 거부한다")
    void createAiDraftRejectsLocationGroupMismatchTest() {
        FlowCreateRequest request = aiDraftRequest(2L);
        when(flowRepository.findByGroupIdAndLocationIdAndNameAndStatusNot(
                GROUP_ID, 2L, request.name(), FlowStatus.ARCHIVED))
                .thenReturn(Optional.empty());
        when(coreActuatorClient.getLocation(2L))
                .thenReturn(new LocationResponse(2L, 999L, "다른 그룹 회의실", LocationResponse.AutoControlMode.AI_DIRECT));

        assertThrows(ForbiddenException.class, () -> flowService.createAiDraft(GROUP_ID, request));

        verify(flowRepository, never()).save(any(Flow.class));
        verifyNoInteractions(nodeRepository, linkRepository);
    }

    @Test
    @DisplayName("AI_DIRECT 모드여도 구조가 실행 불가능하면 INACTIVE로 유지한다")
    void createAiDraftStaysInactiveWhenStructureNotExecutableTest() {
        FlowCreateRequest request = aiDraftRequest(2L);
        when(flowRepository.findByGroupIdAndLocationIdAndNameAndStatusNot(
                GROUP_ID, 2L, request.name(), FlowStatus.ARCHIVED))
                .thenReturn(Optional.empty());
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeRepository.save(any(Node.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coreActuatorClient.getLocation(2L))
                .thenReturn(new LocationResponse(2L, GROUP_ID, "회의실", LocationResponse.AutoControlMode.AI_DIRECT));
        when(flowActivationValidator.validate(any())).thenReturn(List.of(
                new FlowStructureValidationError(
                        FlowStructureErrorCode.SELF_LOOP, null, "nodes", "실행할 수 없는 구조입니다.")));

        FlowResponse response = flowService.createAiDraft(GROUP_ID, request);

        Assertions.assertEquals(FlowStatus.INACTIVE, response.status());
        verifyNoInteractions(activeFlowDefinitionProvider, scheduleFlowScheduler);
    }

    @Test
    @DisplayName("Core 위치 조회가 실패하면 AI_DIRECT 여부를 확인하지 못해 INACTIVE로 생성한다")
    void createAiDraftStaysInactiveWhenCoreLookupFailsTest() {
        FlowCreateRequest request = aiDraftRequest(2L);
        when(flowRepository.findByGroupIdAndLocationIdAndNameAndStatusNot(
                GROUP_ID, 2L, request.name(), FlowStatus.ARCHIVED))
                .thenReturn(Optional.empty());
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeRepository.save(any(Node.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FeignException.InternalServerError coreFailure = mock(FeignException.InternalServerError.class);
        when(coreActuatorClient.getLocation(2L)).thenThrow(coreFailure);

        FlowResponse response = flowService.createAiDraft(GROUP_ID, request);

        Assertions.assertEquals(FlowStatus.INACTIVE, response.status());
        verifyNoInteractions(activeFlowDefinitionProvider, scheduleFlowScheduler);
    }

    // FeignException이 아닌 예외(응답 디코딩 실패 등)도 같은 방식으로 안전하게 처리돼야 합니다.
    @Test
    @DisplayName("Core 위치 조회에서 FeignException이 아닌 예외가 나도 INACTIVE로 생성한다")
    void createAiDraftStaysInactiveWhenCoreLookupThrowsNonFeignExceptionTest() {
        FlowCreateRequest request = aiDraftRequest(2L);
        when(flowRepository.findByGroupIdAndLocationIdAndNameAndStatusNot(
                GROUP_ID, 2L, request.name(), FlowStatus.ARCHIVED))
                .thenReturn(Optional.empty());
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeRepository.save(any(Node.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coreActuatorClient.getLocation(2L)).thenThrow(new RuntimeException("응답 디코딩 실패"));

        FlowResponse response = flowService.createAiDraft(GROUP_ID, request);

        Assertions.assertEquals(FlowStatus.INACTIVE, response.status());
        verifyNoInteractions(activeFlowDefinitionProvider, scheduleFlowScheduler);
    }

    // 404는 "일시적 실패"가 아니라 "locationId가 존재하지 않는다"는 확정된 답이므로, INACTIVE로
    // 넘어가지 않고 저장 전에 거부해야 존재하지 않는 위치를 가리키는 Flow가 남지 않습니다.
    @Test
    @DisplayName("Core가 위치를 찾을 수 없다고(404) 응답하면 저장 전에 거부한다")
    void createAiDraftRejectsWhenLocationNotFoundTest() {
        FlowCreateRequest request = aiDraftRequest(2L);
        when(flowRepository.findByGroupIdAndLocationIdAndNameAndStatusNot(
                GROUP_ID, 2L, request.name(), FlowStatus.ARCHIVED))
                .thenReturn(Optional.empty());
        FeignException.NotFound notFound = mock(FeignException.NotFound.class);
        when(coreActuatorClient.getLocation(2L)).thenThrow(notFound);

        assertThrows(LocationNotFoundException.class, () -> flowService.createAiDraft(GROUP_ID, request));

        verify(flowRepository, never()).save(any(Flow.class));
        verifyNoInteractions(nodeRepository, linkRepository);
    }

    // 이름 유니크 제약상 이 409는 "이미 존재함" 외의 다른 의미를 가질 수 없어서, 재조회를 시도하지 않고
    // 그대로 충돌 예외를 던집니다. 동시에 들어온 같은 요청이 이미 저장을 마쳤다는 뜻이라, 자동화 자체는
    // 문제없이 존재합니다.
    @Test
    @DisplayName("동시 요청으로 저장이 유니크 제약을 위반하면 충돌 예외를 던진다")
    void createAiDraftThrowsOnConcurrentDuplicateTest() {
        FlowCreateRequest request = aiDraftRequest(2L);
        when(flowRepository.findByGroupIdAndLocationIdAndNameAndStatusNot(
                GROUP_ID, 2L, request.name(), FlowStatus.ARCHIVED))
                .thenReturn(Optional.empty());
        when(flowRepository.save(any(Flow.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(DuplicateFlowNameException.class, () -> flowService.createAiDraft(GROUP_ID, request));

        verifyNoInteractions(nodeRepository, linkRepository);
    }

    @Test
    @DisplayName("이름에 [AI] 접두어가 없으면 AI draft 생성을 거부한다")
    void createAiDraftRejectsMissingPrefixTest() {
        FlowCreateRequest request = FlowTestData.createScheduledActuatorFlowRequest(2L);

        assertThrows(InvalidAiDraftNameException.class, () -> flowService.createAiDraft(GROUP_ID, request));

        verifyNoInteractions(flowRepository, coreActuatorClient);
    }

    @Test
    @DisplayName("AI draft는 유저 권한 확인 없이 동작한다")
    void createAiDraftSkipsAuthorizationTest() {
        FlowCreateRequest request = aiDraftRequest(2L);
        when(flowRepository.findByGroupIdAndLocationIdAndNameAndStatusNot(
                GROUP_ID, 2L, request.name(), FlowStatus.ARCHIVED))
                .thenReturn(Optional.empty());
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeRepository.save(any(Node.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coreActuatorClient.getLocation(2L))
                .thenReturn(new LocationResponse(2L, GROUP_ID, "회의실", LocationResponse.AutoControlMode.SUGGESTION));

        flowService.createAiDraft(GROUP_ID, request);

        verifyNoInteractions(groupAuthorizationService);
    }

    // --- 이름 일치 + 내용 비교(갱신) ---

    @Test
    @DisplayName("이름과 내용(cron·액추에이터 명령)이 모두 같으면 기존 그대로 반환하고 아무것도 archive하지 않는다")
    void createAiDraftReturnsUnchangedWhenContentIsSameTest() {
        FlowCreateRequest request = aiDraftRequest(2L);
        Flow existingFlow = new Flow(GROUP_ID, 2L, request.name(), request.description(), FlowStatus.ACTIVE);
        when(flowRepository.findByGroupIdAndLocationIdAndNameAndStatusNot(
                GROUP_ID, 2L, request.name(), FlowStatus.ARCHIVED))
                .thenReturn(Optional.of(existingFlow));
        when(nodeRepository.findByFlowId(existingFlow.getId()))
                .thenReturn(aiDraftNodes("0 0 * * * *", "VENTILATION_FAN", "power", "ON"));

        FlowResponse response = flowService.createAiDraft(GROUP_ID, request);

        Assertions.assertEquals(FlowStatus.ACTIVE, response.status());
        Assertions.assertNull(response.replacedFlowId());
        verify(flowRepository, never()).save(any(Flow.class));
        verify(flowRepository, never()).saveAndFlush(any(Flow.class));
        verifyNoInteractions(coreActuatorClient);
    }

    // 활성화돼 있던 자동화의 트리거 시각만 갱신되는 경우, 사용자가 승인한 자동화의 미세 조정이므로
    // 켜져 있던 상태를 그대로 이어갑니다.
    @Test
    @DisplayName("cron만 다르면 기존을 archive하고 새로 만들되 ACTIVE 상태를 승계한다")
    void createAiDraftCarriesOverActiveStatusWhenOnlyCronChangedTest() {
        FlowCreateRequest request = aiDraftRequestWithNodes(
                2L, "0 30 * * * *", "VENTILATION_FAN", "power", "ON");
        Flow existingFlow = new Flow(GROUP_ID, 2L, request.name(), request.description(), FlowStatus.ACTIVE);
        when(flowRepository.findByGroupIdAndLocationIdAndNameAndStatusNot(
                GROUP_ID, 2L, request.name(), FlowStatus.ARCHIVED))
                .thenReturn(Optional.of(existingFlow));
        when(nodeRepository.findByFlowId(existingFlow.getId()))
                .thenReturn(aiDraftNodes("0 0 * * * *", "VENTILATION_FAN", "power", "ON"));
        when(flowRepository.saveAndFlush(existingFlow)).thenReturn(existingFlow);
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeRepository.save(any(Node.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coreActuatorClient.getLocation(2L))
                .thenReturn(new LocationResponse(2L, GROUP_ID, "회의실", LocationResponse.AutoControlMode.SUGGESTION));

        FlowResponse response = flowService.createAiDraft(GROUP_ID, request);

        Assertions.assertEquals(FlowStatus.ARCHIVED, existingFlow.getStatus());
        Assertions.assertEquals(existingFlow.getId(), response.replacedFlowId());
        Assertions.assertEquals(FlowStatus.ACTIVE, response.status());
        verify(flowRepository).saveAndFlush(existingFlow);
    }

    @Test
    @DisplayName("cron만 다르고 기존이 INACTIVE였으면(SUGGESTION) 새로 만들어도 INACTIVE로 유지한다")
    void createAiDraftKeepsInactiveWhenOnlyCronChangedAndWasInactiveTest() {
        FlowCreateRequest request = aiDraftRequestWithNodes(
                2L, "0 30 * * * *", "VENTILATION_FAN", "power", "ON");
        Flow existingFlow = new Flow(GROUP_ID, 2L, request.name(), request.description(), FlowStatus.INACTIVE);
        when(flowRepository.findByGroupIdAndLocationIdAndNameAndStatusNot(
                GROUP_ID, 2L, request.name(), FlowStatus.ARCHIVED))
                .thenReturn(Optional.of(existingFlow));
        when(nodeRepository.findByFlowId(existingFlow.getId()))
                .thenReturn(aiDraftNodes("0 0 * * * *", "VENTILATION_FAN", "power", "ON"));
        when(flowRepository.saveAndFlush(existingFlow)).thenReturn(existingFlow);
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeRepository.save(any(Node.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coreActuatorClient.getLocation(2L))
                .thenReturn(new LocationResponse(2L, GROUP_ID, "회의실", LocationResponse.AutoControlMode.SUGGESTION));

        FlowResponse response = flowService.createAiDraft(GROUP_ID, request);

        Assertions.assertEquals(FlowStatus.INACTIVE, response.status());
        // registerAfterCommit은 activateAiDraft()에서만 호출되므로, 활성화가 아예 시도되지
        // 않았음을 이걸로 확인합니다(archive 단계의 refreshAfterCommit/cancelAfterCommit은
        // ACTIVE 승계 여부와 무관하게 항상 호출됩니다).
        verify(scheduleFlowScheduler, never()).registerAfterCommit(any(), any());
    }

    // 액추에이터 명령 자체가 바뀌면 물리적으로 수행하는 동작이 달라지는 것이므로, 기존에 켜져
    // 있었더라도 사용자 재승인 없이 자동으로 켜지지 않습니다.
    @Test
    @DisplayName("액추에이터 명령이 다르면 기존이 ACTIVE였어도 새 flow는 INACTIVE로 만든다")
    void createAiDraftStaysInactiveWhenActuatorCommandChangedTest() {
        FlowCreateRequest request = aiDraftRequestWithNodes(
                2L, "0 0 * * * *", "VENTILATION_FAN", "power", "OFF");
        Flow existingFlow = new Flow(GROUP_ID, 2L, request.name(), request.description(), FlowStatus.ACTIVE);
        when(flowRepository.findByGroupIdAndLocationIdAndNameAndStatusNot(
                GROUP_ID, 2L, request.name(), FlowStatus.ARCHIVED))
                .thenReturn(Optional.of(existingFlow));
        when(nodeRepository.findByFlowId(existingFlow.getId()))
                .thenReturn(aiDraftNodes("0 0 * * * *", "VENTILATION_FAN", "power", "ON"));
        when(flowRepository.saveAndFlush(existingFlow)).thenReturn(existingFlow);
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeRepository.save(any(Node.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coreActuatorClient.getLocation(2L))
                .thenReturn(new LocationResponse(2L, GROUP_ID, "회의실", LocationResponse.AutoControlMode.SUGGESTION));

        FlowResponse response = flowService.createAiDraft(GROUP_ID, request);

        Assertions.assertEquals(FlowStatus.ARCHIVED, existingFlow.getStatus());
        Assertions.assertEquals(FlowStatus.INACTIVE, response.status());
    }

    // AI_DIRECT 위치는 무엇이 바뀌었든(cron이든 액추에이터 명령이든) 기존 로직대로 항상 자동
    // 활성화합니다.
    @Test
    @DisplayName("AI_DIRECT 위치는 액추에이터 명령이 바뀌어도 새 flow를 자동 활성화한다")
    void createAiDraftActivatesOnActuatorChangeWhenAiDirectTest() {
        FlowCreateRequest request = aiDraftRequestWithNodes(
                2L, "0 0 * * * *", "VENTILATION_FAN", "power", "OFF");
        Flow existingFlow = new Flow(GROUP_ID, 2L, request.name(), request.description(), FlowStatus.ACTIVE);
        when(flowRepository.findByGroupIdAndLocationIdAndNameAndStatusNot(
                GROUP_ID, 2L, request.name(), FlowStatus.ARCHIVED))
                .thenReturn(Optional.of(existingFlow));
        when(nodeRepository.findByFlowId(existingFlow.getId()))
                .thenReturn(aiDraftNodes("0 0 * * * *", "VENTILATION_FAN", "power", "ON"));
        when(flowRepository.saveAndFlush(existingFlow)).thenReturn(existingFlow);
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeRepository.save(any(Node.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coreActuatorClient.getLocation(2L))
                .thenReturn(new LocationResponse(2L, GROUP_ID, "회의실", LocationResponse.AutoControlMode.AI_DIRECT));

        FlowResponse response = flowService.createAiDraft(GROUP_ID, request);

        Assertions.assertEquals(FlowStatus.ACTIVE, response.status());
        // 한 번은 archive 단계에서(기존 flow), 한 번은 activateAiDraft에서(새 flow) 호출됩니다.
        verify(activeFlowDefinitionProvider, times(2)).refreshAfterCommit(GROUP_ID, 2L);
        verify(scheduleFlowScheduler).registerAfterCommit(eq(GROUP_ID), any());
    }

    private List<Node> aiDraftNodes(String cron, String actuatorType, String command, String commandValue) {
        return List.of(
                new Node(10L, NodeType.SCHEDULE, JsonNodeFactory.instance.objectNode().put("cron", cron)),
                new Node(10L, NodeType.ACTUATOR_CONTROL, JsonNodeFactory.instance.objectNode()
                        .put("actuatorType", actuatorType)
                        .put("command", command)
                        .put("commandValue", commandValue))
        );
    }

    private FlowCreateRequest aiDraftRequestWithNodes(
            Long locationId, String cron, String actuatorType, String command, String commandValue) {
        FlowCreateRequest base = aiDraftRequest(locationId);
        List<FlowNodeRequest> nodes = List.of(
                FlowNodeRequest.builder()
                        .clientNodeKey("hourly_schedule")
                        .nodeType(NodeType.SCHEDULE)
                        .configuration(JsonNodeFactory.instance.objectNode().put("cron", cron))
                        .build(),
                FlowNodeRequest.builder()
                        .clientNodeKey("fan_actuator")
                        .nodeType(NodeType.ACTUATOR_CONTROL)
                        .configuration(JsonNodeFactory.instance.objectNode()
                                .put("actuatorType", actuatorType)
                                .put("command", command)
                                .put("commandValue", commandValue))
                        .build()
        );
        return FlowCreateRequest.builder()
                .locationId(base.locationId())
                .name(base.name())
                .description(base.description())
                .nodes(nodes)
                .links(base.links())
                .build();
    }

    // 공용 SCHEDULE->ACTUATOR_CONTROL 픽스처를 엔진이 요구하는 "[AI] " 접두어 이름으로 감싸 재사용합니다.
    private FlowCreateRequest aiDraftRequest(Long locationId) {
        FlowCreateRequest base = FlowTestData.createScheduledActuatorFlowRequest(locationId);
        return FlowCreateRequest.builder()
                .locationId(base.locationId())
                .name("[AI] " + base.name())
                .description(base.description())
                .nodes(base.nodes())
                .links(base.links())
                .build();
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
