package com.nhnacademy.insightonruleengine.node.domain.params;

import jakarta.validation.constraints.NotBlank;

/**
 * node_type = SCHEDULE.
 * 텔레메트리 이벤트 없이 cron 스케줄러가 FlowRunner를 직접 호출
 * 다중 인스턴스 중복 실행 방지 방식은 미확정 상태다.
 */
public record ScheduleParams(
        @NotBlank String cron
) implements NodeParams {
}
