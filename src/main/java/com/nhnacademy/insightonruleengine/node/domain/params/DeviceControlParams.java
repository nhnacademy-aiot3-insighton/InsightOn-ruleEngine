package com.nhnacademy.insightonruleengine.node.domain.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * node_type = DEVICE_CONTROL.
 * Core 제어 API를 Feign으로 동기 호출. 실행 후 Core의
 * simulator_run_logs.executed_by_type='RULE_ENGINE' 기록을 요청함
 */
public record DeviceControlParams(
        @NotNull Long deviceId,
        @NotBlank String command
) implements NodeParams {
}
