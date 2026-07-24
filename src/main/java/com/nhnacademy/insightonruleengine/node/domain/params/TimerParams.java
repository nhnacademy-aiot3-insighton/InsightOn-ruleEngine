package com.nhnacademy.insightonruleengine.node.domain.params;

import jakarta.validation.constraints.Positive;

/**
 * node_type = TIMER.
 * (node_id, location_id) 조합별로 intervalSeconds당 최초 1회만 통과시킨다 (FR-06).
 * 상태는 인메모리가 아니라 Redis에 원자적으로 관리해 고정 2인스턴스 간 공유해야 한다.
 */
public record TimerParams(
        @Positive int intervalSeconds
) implements NodeParams {
}
