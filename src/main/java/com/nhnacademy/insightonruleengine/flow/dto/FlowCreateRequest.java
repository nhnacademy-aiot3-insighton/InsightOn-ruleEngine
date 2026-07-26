package com.nhnacademy.insightonruleengine.flow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FlowCreateRequest(
        @NotNull
        Long locationId,
        @NotBlank
        @Size(max = 100)
        String name,
        String description
        //List<NodeRequest> nodes,
        //List<LinkRequest> links
) {
}
