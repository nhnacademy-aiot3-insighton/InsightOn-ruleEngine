package com.nhnacademy.insightonruleengine.flow.domain.exception;

import com.nhnacademy.insightonruleengine.common.exception.EngineException;
import com.nhnacademy.insightonruleengine.common.exception.ErrorCode;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;

public class FlowNotActiveException extends EngineException {
    // 플로우 상태
    public FlowNotActiveException(Long flowId, FlowStatus status) {
        super(
                ErrorCode.FLOW_NOT_ACTIVE,
                "Active 상태의 플로우만 활동 가능합니다. flowId=%d, status=%s"
                        .formatted(flowId, status)
        );
    }
}
