package com.nhnacademy.insightonruleengine.flow.api.dto;

import com.nhnacademy.insightonruleengine.flow.domain.definition.LinkDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;

/** 저장된 전체 규칙 기반 플로우 구성을 외부 상세 응답으로 전달한다. */
@Builder
public record FlowDefinitionResponse(
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
}
