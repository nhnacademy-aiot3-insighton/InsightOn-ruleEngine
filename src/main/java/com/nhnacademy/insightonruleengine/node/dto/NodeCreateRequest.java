package com.nhnacademy.insightonruleengine.node.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.nhnacademy.insightonruleengine.node.domain.NodeType;
import jakarta.validation.constraints.NotNull;

public record NodeCreateRequest(
        @NotNull NodeType.Category category,
        @NotNull NodeType nodeType,
        @NotNull JsonNode configuration
) {
}
