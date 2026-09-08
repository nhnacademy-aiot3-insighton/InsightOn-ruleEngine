package com.nhnacademy.insightonruleengine.flow.api.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record FlowNodeRequest(
        @NotBlank
        @Size(max = 100)
        String clientNodeKey,
        @NotNull
        NodeType nodeType,
        @NotNull
        JsonNode configuration
) {
}
