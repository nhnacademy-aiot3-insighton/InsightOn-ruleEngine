package com.nhnacademy.insightonruleengine.flow.domain.node.params.action;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.NodeParams;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * node_type = EXTERNAL_NOTIFICATION.
 * Telegram/이메일로 베스트 에포트 발송한다. 실패해도 재시도·기록을 남기지
 * 않는다(확정 사항) — 이 실패는 GlobalExceptionHandler까지 절대 전파되면 안 된다.
 *
 */
public record ExternalNotificationParams(
        @NotBlank 
        @Pattern(regexp = "TELEGRAM|EMAIL")
        String channel   // "TELEGRAM" | "EMAIL"
) implements NodeParams {
}
