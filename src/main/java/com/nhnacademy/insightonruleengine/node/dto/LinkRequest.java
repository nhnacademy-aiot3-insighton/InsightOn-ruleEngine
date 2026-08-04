package com.nhnacademy.insightonruleengine.node.dto;

import jakarta.validation.constraints.NotBlank;

/**
 *
 * @param sourceTempId
 * @param sourcePort
 * @param targetTempId
 * @param targetPort
 */
public record LinkRequest(
        @NotBlank String sourceTempId,
        @NotBlank String sourcePort,
        @NotBlank String targetTempId,
        @NotBlank String targetPort
) {
}
