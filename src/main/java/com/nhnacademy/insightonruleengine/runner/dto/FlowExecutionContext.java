package com.nhnacademy.insightonruleengine.runner.dto;

import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import java.time.Instant;
import java.util.Map;

public final class FlowExecutionContext {

    private final FlowDefinition flow;
    private SensorEvent event;

    public FlowExecutionContext(FlowDefinition flow, SensorEvent event) {
        if (flow == null) {
            throw new IllegalArgumentException("flow는 필수입니다.");
        }
        if (event == null) {
            throw new IllegalArgumentException("event는 필수입니다.");
        }
        if (event.metrics() == null || event.metrics().isEmpty()) {
            throw new IllegalArgumentException("event metrics는 필수입니다.");
        }
        this.flow = flow;
        this.event = event;
    }

    public FlowDefinition flow() {
        return flow;
    }

    public SensorEvent event() {
        return event;
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

    /** LOCATION trigger가 만든 최신 location metric snapshot을 이후 노드에 전달합니다. */
    public void replaceMetrics(Map<String, Object> metrics) {
        event = new SensorEvent(
                event.groupId(),
                event.locationId(),
                event.sensorId(),
                metrics,
                event.timestamp()
        );
    }
}
