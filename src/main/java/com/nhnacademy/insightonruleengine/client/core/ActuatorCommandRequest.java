package com.nhnacademy.insightonruleengine.client.core;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.ActuatorControlParams;

public record ActuatorCommandRequest(
        String actuatorType,
        String command,
        String commandValue,
        String callerService
) {

    private static final String RULE_ENGINE = "RULE_ENGINE";

    public static ActuatorCommandRequest from(ActuatorControlParams params) {
        if (params == null) {
            throw new IllegalArgumentException("Actuator Control 설정은 필수입니다.");
        }
        return new ActuatorCommandRequest(
                params.actuatorType(),
                params.command(),
                params.commandValue(),
                RULE_ENGINE
        );
    }
}
