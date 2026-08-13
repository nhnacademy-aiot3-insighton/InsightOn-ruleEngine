package com.nhnacademy.insightonruleengine.flow.domain.node.params.action;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.NodeParams;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * ALERT Action의 AI 출력값과 반복 도달·Cooldown 설정입니다. 실제 Alert는 InsightOn AI가 소유한 Queue로 전달될 이벤트로 발행됩니다.
 */
public record AlertParams(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Pattern(regexp = "INFO|WARNING|CRITICAL") String severity,
        @NotBlank String message,
        @Min(1) Integer requiredCount,
        @Min(1) Integer countTimeoutSeconds,
        @Min(0) Integer cooldownSeconds
) implements NodeParams {
    public AlertParams {
        if (requiredCount == null) {
            requiredCount = 1;
        }
        if (cooldownSeconds == null) {
            cooldownSeconds = 0;
        }
        if (requiredCount >= 2 && (countTimeoutSeconds == null || countTimeoutSeconds <= 0)) {
            throw new IllegalArgumentException(
                    "requiredCount가 2 이상이면 countTimeoutSeconds는 양수여야 합니다."
            );
        }
    }
}
