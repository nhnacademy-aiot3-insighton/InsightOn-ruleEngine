package com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq.dto;

import java.util.List;

public record GroupDeletedEvent(
        Long groupId,
        List<Long> locationIds
) {

    public void validate() {
        if (groupId == null || groupId <= 0L) {
            throw new IllegalArgumentException("groupId는 양수여야 합니다.");
        }
        if (locationIds == null
                || locationIds.stream().anyMatch(locationId -> locationId == null || locationId <= 0L)) {
            throw new IllegalArgumentException("locationIds에는 양수만 사용할 수 있습니다.");
        }
    }
}
