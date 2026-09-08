package com.nhnacademy.insightonruleengine.flow.domain.node.params.filter;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.NodeParams;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * node_type = THRESHOLD.
 * expression은 저장 시점에 SpEL 문법 검증을 거치고,
 * 평가는 반드시 SimpleEvaluationContext로 제한한다.
 * 각 실행은 현재 SensorEvent의 metrics만 사용하며, LOCATION도 여러 센서의 값을 합치지 않는다.
 * 조회한 metric이 없거나 null, 비숫자 또는 유한하지 않은 숫자이면 조건은 충족되지 않는다.
 * 여러 조건의 AND 결합은 이 타입을 Link로 직렬 연결하는 것으로 표현한다.
 */
public record ThresholdParams(
        @NotBlank @Size(max = 1000) String expression
) implements NodeParams {
}
