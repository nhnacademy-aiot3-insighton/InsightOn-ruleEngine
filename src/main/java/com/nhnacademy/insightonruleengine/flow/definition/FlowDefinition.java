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
    // 전달된 목록을 copyOf()로 복사, null이면 빈 리스트
    public FlowDefinition {
        nodes = nodes != null ? List.copyOf(nodes) : List.of();
        links = links != null ? List.copyOf(links) : List.of();
    }
}
