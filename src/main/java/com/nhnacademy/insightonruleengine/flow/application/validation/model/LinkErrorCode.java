package com.nhnacademy.insightonruleengine.flow.domain;

public enum LinkErrorCode implements FlowValidationErrorReason {

    //링크는 필수입니다.
    EMPTY_LINKS,
    //링크는 null일 수 없습니다.
    NULL_LINK,
    //sourceClientNodeKey는 필수입니다.
    MISSING_SOURCE_CLIENT_NODE_KEY,
    //targetClientNodeKey는 필수입니다.
    MISSING_TARGET_CLIENT_NODE_KEY,
    //sourcePort는 필수입니다.
    MISSING_SOURCE_PORT,
    //targetPort는 필수입니다.
    MISSING_TARGET_PORT
}
