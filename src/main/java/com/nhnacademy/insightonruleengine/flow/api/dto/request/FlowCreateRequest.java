package com.nhnacademy.insightonruleengine.flow.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Builder;

@Builder
public record FlowCreateRequest(
        @NotNull @Positive
        Long locationId,

        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 2000)
        String description,

        @NotNull @Size(min = 2, max = 500)
        List<@Valid FlowNodeRequest> nodes,

        @NotNull @Size(max = 1000)
        List<@Valid FlowLinkRequest> links
) {
}
