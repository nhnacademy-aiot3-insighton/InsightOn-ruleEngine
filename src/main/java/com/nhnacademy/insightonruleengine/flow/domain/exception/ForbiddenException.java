package com.nhnacademy.insightonruleengine.flow.domain.exception;

import com.nhnacademy.insightonruleengine.common.exception.EngineException;
import com.nhnacademy.insightonruleengine.common.exception.ErrorCode;

public class ForbiddenException extends EngineException {

    public ForbiddenException(String message) {
        super(ErrorCode.FLOW_FORBIDDEN, message);
    }
}
