package com.nhnacademy.insightonruleengine.flow.domain.node.params.filter;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.NodeParams;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * node_type = TIME_WINDOW.
 * 실행 시각이 [startTime, endTime) 범위 내인지만 평가한다. 상태 저장 없음.
 * startTime이 endTime보다 늦으면 자정을 지나는 구간으로 해석한다.
 */
public record TimeWindowParams(
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
) implements NodeParams {
    public TimeWindowParams {
        if (startTime != null && startTime.equals(endTime)) {
            throw new IllegalArgumentException("startTime과 endTime이 같을 수 없습니다.");
        }
    }
}
