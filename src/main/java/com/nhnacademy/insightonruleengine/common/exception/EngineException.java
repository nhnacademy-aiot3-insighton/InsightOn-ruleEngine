package com.nhnacademy.insightonruleengine.common.exception;

import java.util.Objects;

/**
 * 도메인별 예외(FlowException 등)는 전부 상속받게 함
 */
public abstract class EngineException extends RuntimeException {

    private final ErrorCode errorCode;

    protected EngineException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    protected EngineException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
