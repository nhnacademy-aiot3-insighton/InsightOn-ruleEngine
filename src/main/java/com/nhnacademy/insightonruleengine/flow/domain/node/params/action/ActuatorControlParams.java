package com.nhnacademy.insightonruleengine.flow.domain.node.params.action;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.NodeParams;
import jakarta.validation.constraints.NotBlank;

/**
 * node_type = ACTUATOR_CONTROL.
 * 실제 장치 제어는 Core가 담당하며, Engine은 이 설정을 Core 명령 DTO로 변환해 발행한다.
 * callerService는 사용자가 설정하지 않고 Engine이 outbound DTO에 주입한다.
 */
public record ActuatorControlParams(
        @NotBlank String actuatorType,
        @NotBlank String command,
        @NotBlank String commandValue
) implements NodeParams {
}
