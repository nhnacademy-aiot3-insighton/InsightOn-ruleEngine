package com.nhnacademy.insightonruleengine.flow.domain.definition;

public record LinkDefinition(
        Long linkId,
        Long flowId,
        Long sourceNodeId,
        Long targetNodeId,
        String sourcePort,
        String targetPort
) {
}
