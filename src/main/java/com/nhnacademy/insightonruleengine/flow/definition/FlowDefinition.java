package com.nhnacademy.insightonruleengine.flow.definition;

import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record FlowDefinition(
        Long flowId,
        Long groupId,
        Long locationId,
        String name,
        String description,
        FlowStatus status,
        OffsetDateTime createdAt,
        List<NodeDefinition> nodes,
        List<LinkDefinition> links
) {
    public FlowDefinition {
        nodes = nodes != null ? List.copyOf(nodes) : List.of();
        links = links != null ? List.copyOf(links) : List.of();
    }
}
