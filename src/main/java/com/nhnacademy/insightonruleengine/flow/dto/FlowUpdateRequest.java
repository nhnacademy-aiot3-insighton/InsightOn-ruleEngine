package com.nhnacademy.insightonruleengine.flow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Builder;

@Builder
public record FlowUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String name,
        String description,
        @NotEmpty
        List<@Valid FlowNodeRequest> nodes,
        @NotEmpty
        List<@Valid FlowLinkRequest> links
) {
}
