package com.nhnacademy.insightonruleengine.flow.application.authorization;

//그룹의 맴버, 매니저, 슈퍼매니저를 만들어 주고 level에 따라 권한을 부여하기 위한 enum
public enum GroupRole {
    MEMBER(0),
    MANAGER(1),
    SUPER_MANAGER(2);

    private final int ordinal;

    GroupRole(int ordinal) {
        this.ordinal = ordinal;
    }

    public boolean isAtLeast(GroupRole minimum) {
        if (minimum == null) {
            throw new IllegalArgumentException("그룹 역할은 null이면 안됩니다.");
        }
        return this.ordinal >= minimum.ordinal();
    }
}
