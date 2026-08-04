package com.nhnacademy.insightonruleengine.flow.domain.node.params.trigger;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.NodeParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.validation.ValidCron;
import jakarta.validation.constraints.NotBlank;

/**
 * node_type = SCHEDULE.
 * 텔레메트리 이벤트 없이 cron 스케줄러가 FlowRunner를 직접 호출
 * 다중 인스턴스 중복 실행 방지 방식은 미확정 상태다.
 */
public record ScheduleParams(
        @NotBlank
        @ValidCron
        String cron
) implements NodeParams {
}
