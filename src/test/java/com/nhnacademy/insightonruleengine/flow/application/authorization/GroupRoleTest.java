package com.nhnacademy.insightonruleengine.flow.application.authorization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupRoleTest {

    // 같은 역할과 상위 역할이 최소 권한을 만족하는지 확인합니다.
    @Test
    @DisplayName("MANAGER와 SUPER_MANAGER는 MANAGER 최소 역할을 충족한다")
    void managerTest() {
        assertTrue(GroupRole.MANAGER.isAtLeast(GroupRole.MANAGER));
        assertTrue(GroupRole.SUPER_MANAGER.isAtLeast(GroupRole.MANAGER));
    }

    // 읽기 권한은 모든 그룹 역할에 열려 있는지 확인합니다.
    @Test
    @DisplayName("모든 그룹 역할은 MEMBER 최소 역할을 충족한다")
    void allRolesTest() {
        assertTrue(GroupRole.MEMBER.isAtLeast(GroupRole.MEMBER));
        assertTrue(GroupRole.MANAGER.isAtLeast(GroupRole.MEMBER));
        assertTrue(GroupRole.SUPER_MANAGER.isAtLeast(GroupRole.MEMBER));
    }

    // 일반 멤버가 쓰기 작업의 최소 역할을 통과하지 못하도록 확인합니다.
    @Test
    @DisplayName("MEMBER는 MANAGER 최소 역할을 충족하지 못한다")
    void memberCantTest() {
        assertFalse(GroupRole.MEMBER.isAtLeast(GroupRole.MANAGER));
    }

    // 최소 역할을 빠뜨린 호출이 잘못된 권한 결과를 만들지 않도록 확인합니다.
    @Test
    @DisplayName("최소 역할이 null이면 예외가 발생한다")
    void roleNullTest() {
        assertThrows(IllegalArgumentException.class, () -> GroupRole.MEMBER.isAtLeast(null));
    }
}
