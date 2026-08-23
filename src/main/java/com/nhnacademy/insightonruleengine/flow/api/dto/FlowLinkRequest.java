package com.nhnacademy.insightonruleengine.flow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record FlowLinkRequest(
        @NotBlank
        @Size(max = 100)
        String sourceClientNodeKey,
        @NotBlank
        @Size(max = 100)
        String targetClientNodeKey,
        @NotBlank
        @Size(max = 50)
        String sourcePort,
        @NotBlank
        @Size(max = 50)
        String targetPort
) {
}
