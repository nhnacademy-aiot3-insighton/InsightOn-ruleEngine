package com.nhnacademy.insightonruleengine.flow.domain;

public enum FlowStatus {
    ACTIVE, // 활동
    INACTIVE, // 비활
    ARCHIVED, // 휴지통
    ERROR  // node에서 문제가 있을 시의 상태 추후 관련 로직 구현 예정
}
