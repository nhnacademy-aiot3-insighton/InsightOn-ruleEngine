package com.nhnacademy.insightonruleengine.flow.exception;

import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;


public class FlowDeletionNotAllowedException extends RuntimeException {

    public FlowDeletionNotAllowedException(Long flowId, FlowStatus status) {
        super("휴지통에 있는 플로우만 삭제 가능합니다: flowId=%d, status=%s".formatted(flowId, status));
    }
}
