package com.nhnacademy.insightonruleengine.flow.exception;

public class InvalidFlowRestoreException extends RuntimeException {
    //Archive인 플로우를 복구할때 다른 타입이면 에러
    public InvalidFlowRestoreException(String message) {
        super(message);
    }
}
