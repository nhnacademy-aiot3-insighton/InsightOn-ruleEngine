package com.nhnacademy.insightonruleengine.runner.dto;

public record NodeExecutionResult(
        String outputPort,
        boolean terminal
) {

    public static NodeExecutionResult next(String outputPort) {
        if (outputPort == null || outputPort.isBlank()) {
            throw new IllegalArgumentException("outputPort는 필수입니다.");
        }
        return new NodeExecutionResult(outputPort, false);
    }

    public static NodeExecutionResult complete() {
        return new NodeExecutionResult(null, true);
    }
}
