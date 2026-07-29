package com.nhnacademy.insightonruleengine.node.domain.params;

import jakarta.validation.constraints.NotBlank;

/**
 * node_type = ALERT.
 * 목적지가 Core API인지 AI 서비스 API인지 미확정 상태다.
 * 확정 전까지 AlertExecutor는 인터페이스로 분리해서 Mock으로 둔다
 */
public record AlertParams(
        @NotBlank String severity,
        @NotBlank String message
) implements NodeParams {
}
