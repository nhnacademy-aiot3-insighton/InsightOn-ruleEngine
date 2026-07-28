package com.nhnacademy.insightonruleengine.node.domain.params.action;


import com.nhnacademy.insightonruleengine.node.domain.params.NodeParams;
import jakarta.validation.constraints.NotBlank;

/**
 */
public record ScheduleParams(
        @NotBlank String cron
) implements NodeParams {
}
