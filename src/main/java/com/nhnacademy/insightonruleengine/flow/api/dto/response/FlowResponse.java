package com.nhnacademy.insightonruleengine.flow.api.dto;

import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record FlowResponse(
        Long flowId,
        Long groupId,
        Long locationId,
        String name,
        String description,
        FlowStatus status,
        OffsetDateTime createdAt
) {
}
