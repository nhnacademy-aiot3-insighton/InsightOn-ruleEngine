package com.nhnacademy.insightonruleengine.flow.domain.exception;

import com.nhnacademy.insightonruleengine.common.exception.EngineException;
import com.nhnacademy.insightonruleengine.common.exception.ErrorCode;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;


public class InvalidFlowStatusTransitionException extends EngineException {

    public InvalidFlowStatusTransitionException(FlowStatus currentStatus, FlowStatus targetStatus) {
        super(
                ErrorCode.FLOW_INVALID_STATUS_TRANSITION,
                "허용되지 않은 플로우 상태 변형: %s -> %s".formatted(currentStatus, targetStatus)
        );
    }
}
