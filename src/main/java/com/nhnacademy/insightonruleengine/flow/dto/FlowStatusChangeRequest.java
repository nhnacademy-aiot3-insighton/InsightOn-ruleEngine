package com.nhnacademy.insightonruleengine.flow.dto;

import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import jakarta.validation.constraints.NotNull;

public record FlowStatusChangeRequest(
        @NotNull
        FlowStatus status
) {
}
