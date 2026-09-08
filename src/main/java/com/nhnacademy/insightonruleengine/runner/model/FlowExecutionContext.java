package com.nhnacademy.insightonruleengine.runner.model;

import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import java.time.Instant;
import java.util.Map;

public record FlowExecutionContext(
        FlowDefinition flow,
        ExecutionTriggerType triggerType,
        SensorEvent event,
        Instant timestamp
) {

    public FlowExecutionContext {
        if (flow == null) {
            throw new IllegalArgumentException("flow는 필수입니다.");
        }
        if (triggerType == null) {
            throw new IllegalArgumentException("triggerType은 필수입니다.");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp는 필수입니다.");
        }
        if (triggerType == ExecutionTriggerType.TELEMETRY) {
            if (event == null) {
                throw new IllegalArgumentException("Telemetry 실행에는 event가 필수입니다.");
            }
            if (event.metrics() == null || event.metrics().isEmpty()) {
                throw new IllegalArgumentException("event metrics는 필수입니다.");
            }
        } else if (event != null) {
            throw new IllegalArgumentException("Schedule 실행은 SensorEvent를 가질 수 없습니다.");
        }
    }

    public FlowExecutionContext(FlowDefinition flow, SensorEvent event) {
        this(
                flow,
                ExecutionTriggerType.TELEMETRY,
                event,
                event == null ? null : event.timestamp()
        );
    }

    public static FlowExecutionContext scheduled(FlowDefinition flow, Instant triggeredAt) {
        return new FlowExecutionContext(
                flow,
                ExecutionTriggerType.SCHEDULE,
                null,
                triggeredAt
        );
    }

    public Map<String, Object> metrics() {
        return event == null ? Map.of() : event.metrics();
    }

    public Object metric(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("metric name은 필수입니다.");
        }
        return metrics().get(name);
    }

}
