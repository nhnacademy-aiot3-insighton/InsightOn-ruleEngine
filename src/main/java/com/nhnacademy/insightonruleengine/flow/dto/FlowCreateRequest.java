package com.nhnacademy.insightonruleengine.flow.dto;

public record FlowCreateRequest(
        Long locationId,
        String name,
        String description
) {
}
