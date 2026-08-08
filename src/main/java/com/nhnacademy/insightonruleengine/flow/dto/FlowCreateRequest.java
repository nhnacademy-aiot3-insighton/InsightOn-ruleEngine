package com.nhnacademy.insightonruleengine.flow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Builder;

@Builder
public record FlowCreateRequest(
        @NotNull
        Long locationId,

        @NotBlank
        @Size(max = 100)
        String name,

        String description,

        @NotNull
        List<FlowNodeRequest> nodes,

        @NotNull
        List<FlowLinkRequest> links
) {
}
