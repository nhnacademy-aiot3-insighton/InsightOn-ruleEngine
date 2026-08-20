package com.nhnacademy.insightonruleengine.flow.domain.node.params.trigger;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.NodeParams;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * node_type = SENSOR.
 * Router가 location_id + sensorId로 이 Flow를 찾아 디스패치한다 (FR-11).
 */
public record SensorParams(
        @NotNull @Positive Long sensorId
) implements NodeParams {
}
