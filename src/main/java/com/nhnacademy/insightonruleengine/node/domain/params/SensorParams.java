package com.nhnacademy.insightonruleengine.node.domain.params;

import jakarta.validation.constraints.NotBlank;

/**
 * node_type = SENSOR.
 * Router가 location_id + devName으로 이 Flow를 찾아 디스패치한다 (FR-11).
 */
public record SensorParams(
        @NotBlank String devName
) implements NodeParams {
}
