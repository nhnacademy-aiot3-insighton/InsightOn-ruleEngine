package com.nhnacademy.insightonruleengine.flow.domain.node.params.action;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.NodeParams;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * node_type = ACTUATOR_CONTROL.
 * 실제 장치 제어는 Core가 담당하며, Engine은 이 설정을 Core 명령 DTO로 변환해 발행한다.
 * callerService는 사용자가 설정하지 않고 Engine이 outbound DTO에 주입한다.
 */
public record ActuatorControlParams(
        @Positive Long deviceId,
        @Size(max = 100) String actuatorType,
        @Size(max = 100) String command,
        @Size(max = 500) String commandValue
) implements NodeParams {

    /** 기존 저장 데이터(deviceId[, command])인지 확인합니다. */
    @JsonIgnore
    public boolean legacyDeviceConfiguration() {
        return deviceId != null;
    }

    /** 기존 계약과 신규 계약이 섞인 모호한 설정은 허용하지 않습니다. */
    @JsonIgnore
    @AssertTrue(message = "deviceId 또는 actuatorType, command, commandValue를 완전하게 설정해야 합니다.")
    public boolean isValidConfiguration() {
        if (legacyDeviceConfiguration()) {
            return isBlank(actuatorType) && isBlank(commandValue);
        }
        return !isBlank(actuatorType) && !isBlank(command) && !isBlank(commandValue);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
