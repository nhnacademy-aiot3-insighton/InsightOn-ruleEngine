package com.nhnacademy.insightonruleengine.node.domain.params;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * node_type = TIME_WINDOW.
 * 현재 시각이 [startTime, endTime) 범위 내인지만 평가한다. 상태 저장 없음.
 */
public record TimeWindowParams(
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
) implements NodeParams {
}
