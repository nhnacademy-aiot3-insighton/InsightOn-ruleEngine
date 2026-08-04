package com.nhnacademy.insightonruleengine.flow.authorization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

//코어에 모르는 추가 필드가 있어도 오류를 내지 말고 무시
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupMemberResponse(
        Long groupId,
        GroupRole groupRole
) {
}
