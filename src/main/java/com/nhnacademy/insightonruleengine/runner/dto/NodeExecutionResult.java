package com.nhnacademy.insightonruleengine.runner.dto;

public record NodeExecutionResult(
        String outputPort,
        boolean terminal
) {

    public NodeExecutionResult {
        if (terminal && outputPort != null) {
            throw new IllegalArgumentException("종료 결과는 outputPort를 가질 수 없습니다.");
        }
        if (!terminal && (outputPort == null || outputPort.isBlank())) {
            throw new IllegalArgumentException("계속 실행 결과는 outputPort가 필요합니다.");
        }
    }

    public static NodeExecutionResult next(String outputPort) {
        return new NodeExecutionResult(outputPort, false);
    }

    public static NodeExecutionResult complete() {
        return new NodeExecutionResult(null, true);
    }
}
