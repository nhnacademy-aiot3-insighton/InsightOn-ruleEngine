package com.nhnacademy.insightonruleengine.flow.exception;

public class CoreDependencyException extends RuntimeException {

    // Core 응답을 정상적으로 해석할 수 없을 때 권한 거부와 구분합니다.
    public CoreDependencyException(String message) {
        super(message);
    }
}
