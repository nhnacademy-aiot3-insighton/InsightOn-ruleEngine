package com.nhnacademy.insightonruleengine.flow.domain.exception;

import com.nhnacademy.insightonruleengine.common.exception.EngineException;
import com.nhnacademy.insightonruleengine.common.exception.ErrorCode;

public class ReservedFlowNamePrefixException extends EngineException {

    public ReservedFlowNamePrefixException(String reservedPrefix, String name) {
        super(
                ErrorCode.FLOW_RESERVED_NAME_PREFIX,
                "\"%s\"로 시작하는 이름은 AI가 만든 Flow 전용입니다: name=%s".formatted(reservedPrefix, name)
        );
    }
}
