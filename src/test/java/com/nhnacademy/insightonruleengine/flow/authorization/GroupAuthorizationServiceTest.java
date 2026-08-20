package com.nhnacademy.insightonruleengine.flow.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.flow.exception.CoreDependencyException;
import com.nhnacademy.insightonruleengine.flow.exception.ForbiddenException;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupAuthorizationServiceTest {

    private static final long GROUP_ID = 1L;
    private static final long USER_ID = 100L;

    @Mock
    private CoreGroupClient coreGroupClient;

    @InjectMocks
    private GroupAuthorizationService groupAuthorizationService;

    // Core가 반환한 같은 그룹의 역할을 한 번의 조회 사용
    @Test
    @DisplayName("같은 그룹의 멤버 역할을 반환하고 Core를 한 번 호출한다")
    void sameGroupTest() {
        when(coreGroupClient.getGroupMemberByUserId(GROUP_ID, USER_ID))
                .thenReturn(new GroupMemberResponse(GROUP_ID, GroupRole.MANAGER));

        GroupRole role = groupAuthorizationService.requireMembership(GROUP_ID, USER_ID);

        assertEquals(GroupRole.MANAGER, role);
        verify(coreGroupClient).getGroupMemberByUserId(GROUP_ID, USER_ID);
    }

    // 200 응답의 본문 누락은 권한 부족이 아니라 Core 응답 계약 문제로 처리합니다.
    @Test
    @DisplayName("Core 응답 body가 null이면 의존성 예외가 발생한다")
    void nullResponseBodyTest() {
        when(coreGroupClient.getGroupMemberByUserId(GROUP_ID, USER_ID)).thenReturn(null);

        assertThrows(
                CoreDependencyException.class,
                () -> groupAuthorizationService.requireMembership(GROUP_ID, USER_ID));
    }

    // Core 응답 그룹이 요청 그룹과 다르면 접근을 거부
    @Test
    @DisplayName("Core 응답 groupId가 요청과 다르면 권한을 거부한다")
    void responseTest() {
        when(coreGroupClient.getGroupMemberByUserId(GROUP_ID, USER_ID))
                .thenReturn(new GroupMemberResponse(2L, GroupRole.MANAGER));

        assertThrows(
                ForbiddenException.class,
                () -> groupAuthorizationService.requireMembership(GROUP_ID, USER_ID));
    }

    // Core 응답에 역할이 없으면 서버 오류 대신 권한 거부로 처리하도록 확인합니다.
    @Test
    @DisplayName("Core 응답 역할이 null이면 권한을 거부한다")
    void responseNullTest() {
        when(coreGroupClient.getGroupMemberByUserId(GROUP_ID, USER_ID))
                .thenReturn(new GroupMemberResponse(GROUP_ID, null));

        assertThrows(
                ForbiddenException.class,
                () -> groupAuthorizationService.requireMembership(GROUP_ID, USER_ID));
    }

    // Core의 멤버 없음 응답이 공통 권한 거부로 변환되는지 확인합니다.
    @Test
    @DisplayName("Core가 멤버를 찾지 못하면 권한을 거부한다")
    void notMemberTest() {
        FeignException.NotFound notFound = mock(FeignException.NotFound.class);
        when(coreGroupClient.getGroupMemberByUserId(GROUP_ID, USER_ID)).thenThrow(notFound);

        assertThrows(
                ForbiddenException.class,
                () -> groupAuthorizationService.requireMembership(GROUP_ID, USER_ID));
    }

    // MEMBER가 쓰기 작업의 MANAGER 최소 역할을 통과하지 못하도록 확인합니다.
    @Test
    @DisplayName("MEMBER에게 MANAGER 권한이 필요하면 접근을 거부한다")
    void notManagerTest() {
        when(coreGroupClient.getGroupMemberByUserId(GROUP_ID, USER_ID))
                .thenReturn(new GroupMemberResponse(GROUP_ID, GroupRole.MEMBER));

        assertThrows(
                ForbiddenException.class,
                () -> groupAuthorizationService.requireRole(GROUP_ID, USER_ID, GroupRole.MANAGER));
        verify(coreGroupClient).getGroupMemberByUserId(GROUP_ID, USER_ID);
    }

    // Core 5xx가 권한 부족으로 오인되지 않고 의존성 실패로 전달되는지 확인합니다.
    @Test
    @DisplayName("Core 5xx는 CoreDependencyException으로 변환한다")
    void exceptionTest() {
        FeignException.InternalServerError coreFailure = mock(FeignException.InternalServerError.class);
        when(coreGroupClient.getGroupMemberByUserId(GROUP_ID, USER_ID)).thenThrow(coreFailure);

        CoreDependencyException thrown = assertThrows(
                CoreDependencyException.class,
                () -> groupAuthorizationService.requireMembership(GROUP_ID, USER_ID));

        assertSame(coreFailure, thrown.getCause());
    }
}
