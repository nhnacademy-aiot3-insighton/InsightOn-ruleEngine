package com.nhnacademy.insightonruleengine.client.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Core의 위치 상세 조회 응답입니다. AI draft를 즉시 ACTIVE로 만들지 판단하기 위해 autoControlMode만 사용합니다.
 */
//코어에 모르는 추가 필드가 있어도 오류를 내지 말고 무시
@JsonIgnoreProperties(ignoreUnknown = true)
public record LocationResponse(
        Long locationId,
        Long groupId,
        String locationName,
        AutoControlMode autoControlMode
) {

    public enum AutoControlMode {
        SUGGESTION,
        AI_DIRECT
    }
}
