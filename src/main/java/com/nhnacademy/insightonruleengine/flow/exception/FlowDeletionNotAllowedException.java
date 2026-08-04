package com.nhnacademy.insightonruleengine.flow.exception;

import com.nhnacademy.insightonruleengine.common.exception.EngineException;
import com.nhnacademy.insightonruleengine.common.exception.ErrorCode;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;


public class FlowDeletionNotAllowedException extends EngineException {

    public FlowDeletionNotAllowedException(Long flowId, FlowStatus status) {
        super(
                ErrorCode.FLOW_DELETION_NOT_ALLOWED,
                "휴지통에 있는 플로우만 삭제 가능합니다: flowId=%d, status=%s"
                        .formatted(flowId, status)
        );
    }
}
