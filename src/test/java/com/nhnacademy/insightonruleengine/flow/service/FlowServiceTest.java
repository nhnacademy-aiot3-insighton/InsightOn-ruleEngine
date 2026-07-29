package com.nhnacademy.insightonruleengine.flow.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.flow.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.authorization.GroupRole;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.dto.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.dto.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowUpdateRequest;
import com.nhnacademy.insightonruleengine.flow.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowDeletionNotAllowedException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowNotFoundException;
import com.nhnacademy.insightonruleengine.flow.exception.ForbiddenException;
import com.nhnacademy.insightonruleengine.flow.exception.InvalidFlowStatusTransitionException;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
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

    @InjectMocks
    FlowService flowService;

    @Test
    @DisplayName("INACTIVE 상태 플로우 생성 테스트")
    void createInactiveFlowTest() {
        FlowCreateRequest flowCreateRequest =
                new FlowCreateRequest(2L, " 온도 알람 ", "온도가 너무 높아요");
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
    @DisplayName("동일 플로우 이름 중복 테스트")
    void sameFlowNameTest() {
        FlowCreateRequest flowCreateRequest = new FlowCreateRequest(1L, "온도", "온도");
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
        FlowCreateRequest request = new FlowCreateRequest(1L, "온도", null);
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

        List<FlowResponse> responses = flowService.findAll(GROUP_ID, USER_ID);

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

        flowService.findAll(GROUP_ID, USER_ID, FlowStatus.ACTIVE);

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

        flowService.findAll(GROUP_ID, USER_ID, 10L, FlowStatus.ACTIVE);

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
        FlowUpdateRequest request = new FlowUpdateRequest("수정 Flow", "수정 설명");
        when(flowRepository.findById(1L)).thenReturn(Optional.of(currentFlow));
        when(flowRepository.existsByGroupIdAndLocationIdAndName(
                1L,
                1L,
                "수정 Flow")).thenReturn(false);
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FlowResponse response = flowService.update(GROUP_ID, USER_ID, 1L, request);

        Assertions.assertEquals(FlowStatus.ARCHIVED, currentFlow.getStatus());
        Assertions.assertEquals(FlowStatus.INACTIVE, response.status());
        verify(groupAuthorizationService).requireRole(GROUP_ID, USER_ID, GroupRole.MANAGER);
    }

    @Test
    @DisplayName("수정 이름 검증이 실패하면 기존 Flow 상태를 유지한다")
    void ValidationFailsTest() {
        Flow currentFlow = new Flow(1L, 1L, "기존 Flow", null, FlowStatus.ACTIVE);
        FlowUpdateRequest request = new FlowUpdateRequest("중복 Flow", null);
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
}
