package com.nhnacademy.insightonruleengine.common.exception;

public record ErrorResponse(
        int status,
        String message
) {
}
