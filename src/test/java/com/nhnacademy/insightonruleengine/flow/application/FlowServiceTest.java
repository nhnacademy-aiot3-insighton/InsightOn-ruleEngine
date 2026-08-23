package com.nhnacademy.insightonruleengine.flow.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.application.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.application.authorization.GroupRole;
import com.nhnacademy.insightonruleengine.runner.cache.ActiveFlowDefinitionProvider;
import com.nhnacademy.insightonruleengine.flow.FlowTestData;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.Link;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.api.dto.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.api.dto.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.FlowUpdateRequest;
import com.nhnacademy.insightonruleengine.flow.domain.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.FlowDeletionNotAllowedException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.FlowNotFoundException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.ForbiddenException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.InvalidFlowStatusTransitionException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.InvalidFlowStructureException;
import com.nhnacademy.insightonruleengine.flow.infrastructure.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.NodeRepository;
import com.nhnacademy.insightonruleengine.flow.application.FlowService;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowStructureValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowActivationValidator;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStructureErrorCode;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.domain.NodeErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    FlowDefinitionAssembler flowDefinitionAssembler;

    @Mock
    FlowActivationValidator flowActivationValidator;

    @Mock
    ActiveFlowDefinitionProvider activeFlowDefinitionProvider;

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

        FlowResponse response = flowService.restore(GROUP_ID, USER_ID, 2L);

        Assertions.assertEquals(FlowStatus.INACTIVE, archivedFlow.getStatus());
        Assertions.assertEquals(FlowStatus.INACTIVE, response.status());
        verify(groupAuthorizationService).requireRole(GROUP_ID, USER_ID, GroupRole.MANAGER);
    }

    @Test
    @DisplayName("ARCHIVED가 아닌 Flow는 복구할 수 없다")
    void rejectNonArchivedFlowRestoreTest() {
        Flow inactiveFlow = new Flow(1L, 1L, "현재 Flow", null, FlowStatus.INACTIVE);
        when(flowRepository.findById(2L)).thenReturn(Optional.of(inactiveFlow));

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
