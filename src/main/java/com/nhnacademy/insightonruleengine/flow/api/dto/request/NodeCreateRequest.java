package com.nhnacademy.insightonruleengine.flow.api.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import jakarta.validation.constraints.NotNull;

public record NodeCreateRequest(
        @NotNull NodeType.Category category,
        @NotNull NodeType nodeType,
        @NotNull JsonNode configuration
) {
}
