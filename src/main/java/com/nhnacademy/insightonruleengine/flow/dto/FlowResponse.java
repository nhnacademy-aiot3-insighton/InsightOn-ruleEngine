package com.nhnacademy.insightonruleengine.flow.dto;

import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import java.time.OffsetDateTime;

public record FlowResponse(
        Long flowId,
        Long groupId,
        Long locationId,
        String name,
        String description,
        FlowStatus status,
        OffsetDateTime createdAt
) {

    // 저장된 Flow를 API 응답값으로 바꿉니다.
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
