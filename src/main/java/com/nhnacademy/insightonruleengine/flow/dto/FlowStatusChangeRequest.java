package com.nhnacademy.insightonruleengine.flow.dto;

import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record FlowStatusChangeRequest(
        @NotNull
        FlowStatus status
) {
}
