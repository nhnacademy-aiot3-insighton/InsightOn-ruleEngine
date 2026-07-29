package com.nhnacademy.insightonruleengine.flow.definition;

public record LinkDefinition(
        Long linkId,
        Long flowId,
        Long sourceNodeId,
        Long targetNodeId,
        String sourcePort,
        String targetPort
) {
}
