package com.nhnacademy.insightonruleengine.flow.domain;

public enum NodeErrorCode implements FlowValidationErrorReason {

    //노드는 필수입니다.
    EMPTY_NODES,
    //노드는 null일 수 없습니다.
    NULL_NODE,
    //clientNodeKey는 필수입니다.
    MISSING_CLIENT_NODE_KEY,
    //노드 타입은 필수입니다.
    MISSING_NODE_TYPE,
    //노드 설정값은 필수입니다.
    MISSING_NODE_CONFIGURATION,
    //노드키가 중복되면 링크가 어디 노드를 가리키는지 결정할 수 없습니다.
    DUPLICATE_CLIENT_NODE_KEY,
    //노드 타입에 맞지 않거나 필수 파라미터가 누락된 설정입니다.
    INVALID_NODE_CONFIGURATION
}
