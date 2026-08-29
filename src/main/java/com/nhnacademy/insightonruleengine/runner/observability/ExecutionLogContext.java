package com.nhnacademy.insightonruleengine.runner.observability;

import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.runner.model.ExecutionTriggerType;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ExecutionLogContext(
        String executionId,
        ExecutionTriggerType triggerType,
        Long flowId,
        Long groupId,
        Long locationId,
        Long sensorId,
        Map<String, Object> metrics,
        Instant timestamp
) {

    public ExecutionLogContext {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId는 필수입니다.");
        }
        if (triggerType == null) {
            throw new IllegalArgumentException("triggerType은 필수입니다.");
        }
        if (flowId == null) {
            throw new IllegalArgumentException("flowId는 필수입니다.");
        }
    }

    public static ExecutionLogContext telemetry(FlowDefinition flow, SensorEvent event) {
        if (flow == null) {
            throw new IllegalArgumentException("flow는 필수입니다.");
        }
        if (event == null) {
            throw new IllegalArgumentException("event는 필수입니다.");
        }

        return new ExecutionLogContext(
                UUID.randomUUID().toString(),
                ExecutionTriggerType.TELEMETRY,
                flow.flowId(),
                event.groupId(),
                event.locationId(),
                event.sensorId(),
                event.metrics(),
                event.timestamp()
        );
    }

    public static ExecutionLogContext scheduled(FlowDefinition flow, Instant triggeredAt) {
        if (flow == null) {
            throw new IllegalArgumentException("flow는 필수입니다.");
        }
        if (triggeredAt == null) {
            throw new IllegalArgumentException("triggeredAt은 필수입니다.");
        }

        return new ExecutionLogContext(
                UUID.randomUUID().toString(),
                ExecutionTriggerType.SCHEDULE,
                flow.flowId(),
                flow.groupId(),
                flow.locationId(),
                null,
                Map.of(),
                triggeredAt
        );
    }

}
