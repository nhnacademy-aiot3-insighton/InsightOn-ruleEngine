package com.nhnacademy.insightonruleengine.flow.exception;

import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;


public class InvalidFlowStatusTransitionException extends RuntimeException {

    public InvalidFlowStatusTransitionException(FlowStatus currentStatus, FlowStatus targetStatus) {
        super("허용되지 않은 플로우 상태 변형: %s -> %s".formatted(currentStatus, targetStatus));
    }
}
