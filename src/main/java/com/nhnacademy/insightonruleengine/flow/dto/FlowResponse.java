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

    // JPA Entity를 API 밖에 직접 노출하지 않도록 응답 계약으로 변환한다.
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
