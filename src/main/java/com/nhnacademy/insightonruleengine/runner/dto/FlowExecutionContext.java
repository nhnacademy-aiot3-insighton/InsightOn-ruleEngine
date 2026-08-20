package com.nhnacademy.insightonruleengine.runner.dto;

import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import java.time.Instant;
import java.util.Map;

public record FlowExecutionContext(
        FlowDefinition flow,
        SensorEvent event
) {

    public FlowExecutionContext {
        if (flow == null) {
            throw new IllegalArgumentException("flow는 필수입니다.");
        }
        if (event == null) {
            throw new IllegalArgumentException("event는 필수입니다.");
        }
        if (event.metrics() == null || event.metrics().isEmpty()) {
            throw new IllegalArgumentException("event metrics는 필수입니다.");
        }
    }

    public Map<String, Object> metrics() {
        return event.metrics();
    }

    public Object metric(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("metric name은 필수입니다.");
        }
        return event.metrics().get(name);
    }

    public Instant timestamp() {
        return event.timestamp();
    }

}
