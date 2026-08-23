package com.nhnacademy.insightonruleengine.flow.domain;

public enum FlowStructureErrorCode implements FlowValidationErrorReason {

    //트리거 노드는 시작점이므로 하나여야합니다.
    INVALID_TRIGGER_COUNT,
    //실행결과를 만드는 액션은 하나라도 있어야합니다.
    MISSING_ACTION,
    //링크의 출발노드가 존재하지 않습니다.
    MISSING_SOURCE_NODE,
    //링크의 도착노드가 존재하지 않습니다.
    MISSING_TARGET_NODE,
    //노드가 자기 자신을 호출하면 즉시 다시 실행합니다. (같은 노드를 연결하면 생기는 문제)
    SELF_LOOP,
    //(sourceNodeId, sourcePort)당 링크는 하나만 지원합니다.
    DUPLICATE_SOURCE_PORT,
    //트리거는 시작점이므로 받는 링크가 없어야합니다.
    TRIGGER_INPUT_LINK,
    //액션은 종착점이므로 나가는 링크가 없어야합니다.
    ACTION_OUTPUT_LINK,
    //액션이 아닌 노드에 출력 링크가 없으면 안됩니다.
    MISSING_OUTPUT_LINK,
    //타겟포트는 in 만 허용합니다(and, or 연산시 확장 가능성)
    INVALID_PORT,
    //트리거에서 도달 할 수 없는 노드가 존재합니다. (예시 트리거 -> 트리거)
    UNREACHABLE_NODE,
    //어떤 Action에도 도달할 수 없는 노드가 존재합니다.
    CANNOT_REACH_ACTION,
    //노드 링크 연결 구조에 순환 경로가 있습니다. (SELF_LOOP와 비슷한 문제 노드 A -> 노드 B -> 노드 A-> 노드 B) 이런 반복을 방지를 위함
    CYCLE_DETECTED
}
