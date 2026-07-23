package com.nhnacademy.insightonruleengine.flow.dto;

import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import java.time.Instant;

public record FlowResponse(
        Long id,
        Long groupId,
        Long locationId,
        String name,
        String description,
        FlowStatus status,
        Instant createdDate
) {
    public static FlowResponse from(Flow flow) {
        return new FlowResponse(
                flow.getId(),
                flow.getGroupId(),
                flow.getLocationId(),
                flow.getName(),
                flow.getDescription(),
                flow.getStatus(),
                flow.getCreatedDate()
        );
    }
}
