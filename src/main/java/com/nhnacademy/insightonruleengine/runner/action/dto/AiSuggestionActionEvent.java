package com.nhnacademy.insightonruleengine.runner.action.dto;

import java.time.OffsetDateTime;

//InsightOn-ai 서비스로 발행하는 AI_SUGGESTION 액션 이벤트 DTO입니다.
public record AiSuggestionActionEvent(
        Long groupId,
        Long locationId,
        Long deviceId,
        String metricKey,
        Double value,
        OffsetDateTime timestamp
) {
    public AiSuggestionActionEvent {
        if (groupId == null || groupId <= 0L) {
            throw new IllegalArgumentException("groupId는 필수이며 양수여야 합니다.");
        }
        if (locationId == null || locationId <= 0L) {
            throw new IllegalArgumentException("locationId는 필수이며 양수여야 합니다.");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp는 필수입니다.");
        }
    }

}
