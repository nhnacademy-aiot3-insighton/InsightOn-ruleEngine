package com.nhnacademy.insightonruleengine.node.domain.params;

import jakarta.validation.constraints.NotBlank;

/**
 * node_type = THRESHOLD.
 * expression은 저장 시점에 SpEL 문법 검증을 거치고,
 * 평가는 반드시 SimpleEvaluationContext로 제한한다
 * 여러 조건의 AND 결합은 이 타입을 Link로 직렬 연결하는 것으로 표현한다.
 */
public record ThresholdParams(
        @NotBlank String expression
) implements NodeParams {
}
