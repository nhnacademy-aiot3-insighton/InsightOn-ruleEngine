package com.nhnacademy.insightonruleengine.flow.exception;

public class FlowNotFoundException extends RuntimeException {

    public FlowNotFoundException(Long groupId, Long flowId) {
        super("플로우를 찾을 수 없습니다: groupId=%d, flowId=%d".formatted(groupId, flowId));
    }
}
