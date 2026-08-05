package com.nhnacademy.insightonruleengine.flow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record FlowCreateRequest(
        @NotNull
        Long locationId,

        @NotBlank
        @Size(max = 100)
        String name,

        String description
) {
}
