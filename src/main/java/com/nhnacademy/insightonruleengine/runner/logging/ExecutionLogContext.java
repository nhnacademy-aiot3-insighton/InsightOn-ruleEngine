package com.nhnacademy.insightonruleengine.runner.logging;

import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ExecutionLogContext(
        String executionId,
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
        if (flowId == null) {
            throw new IllegalArgumentException("flowId는 필수입니다.");
        }
    }

    public static ExecutionLogContext create(FlowDefinition flow, SensorEvent event) {
        if (flow == null) {
            throw new IllegalArgumentException("flow는 필수입니다.");
        }
        if (event == null) {
            throw new IllegalArgumentException("event는 필수입니다.");
        }

        return new ExecutionLogContext(
                UUID.randomUUID().toString(),
                flow.flowId(),
                event.groupId(),
                event.locationId(),
                event.sensorId(),
                event.metrics(),
                event.timestamp()
        );
    }
}
