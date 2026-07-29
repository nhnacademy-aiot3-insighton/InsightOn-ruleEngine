package com.nhnacademy.insightonruleengine.flow.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.nhnacademy.insightonruleengine.node.domain.NodeType;

public record NodeDefinition(
        Long nodeId,
        NodeType nodeType,
        String name,
        JsonNode configuration
) {
    public NodeDefinition {
        if (configuration == null) {
            throw new IllegalArgumentException("configuration은 필수입니다.");
        }
        configuration = configuration.deepCopy();
    }

    @Override
    public JsonNode configuration() {
        return configuration.deepCopy();
    }
}
