package com.nhnacademy.insightonruleengine.runner.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;

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
    }

    public JsonNode payload() {
        return event.payload();
    }
}
