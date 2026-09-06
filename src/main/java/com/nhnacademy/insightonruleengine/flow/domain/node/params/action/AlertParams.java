package com.nhnacademy.insightonruleengine.flow.domain.node.params.action;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.NodeParams;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * ALERT Action이 발행할 메시지 설정입니다. 반복 횟수와 최소 실행 간격은 앞단 EVENT_GATE가 담당합니다.
 */
public record AlertParams(
        @NotBlank @Size(max = 200) String title,
        @NotNull Severity severity,
        @NotBlank @Size(max = 2000) String message
) implements NodeParams {
}
