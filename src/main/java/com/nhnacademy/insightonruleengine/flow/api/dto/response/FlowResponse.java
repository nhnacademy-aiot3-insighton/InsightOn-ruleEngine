package com.nhnacademy.insightonruleengine.flow.api.dto.response;

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
        OffsetDateTime createdAt,
        // AI draft 갱신으로 archive된 기존 Flow의 id. 신규 생성이거나 내용 변경이 없었으면 null.
        Long replacedFlowId
) {
}
