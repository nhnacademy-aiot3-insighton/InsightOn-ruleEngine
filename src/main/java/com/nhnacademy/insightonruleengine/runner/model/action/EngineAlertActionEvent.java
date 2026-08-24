package com.nhnacademy.insightonruleengine.runner.model.action;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.Severity;
import java.util.Map;

//InsightOn-ai 서비스로 발행하는 ALERT 액션 이벤트 dto
public record EngineAlertActionEvent(
        Long groupId,
        Long locationId,
        Long flowId,
        String title,
        String message,
        Severity severity,
        Map<String, Object> triggerValue
) {
    public EngineAlertActionEvent {
        if (groupId == null || groupId <= 0L) {
            throw new IllegalArgumentException("groupId는 필수이며 양수여야 합니다.");
        }
        if (locationId == null || locationId <= 0L) {
            throw new IllegalArgumentException("locationId는 필수이며 양수여야 합니다.");
        }
        if (flowId == null || flowId <= 0L) {
            throw new IllegalArgumentException("flowId는 필수이며 양수여야 합니다.");
        }
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new IllegalArgumentException("title은 비어있을 수 없으며 200자 이하여야 합니다.");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message는 필수입니다.");
        }
        if (severity == null) {
            throw new IllegalArgumentException("severity는 필수입니다.");
        }
    }
}
