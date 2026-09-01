package com.nhnacademy.insightonruleengine.flow.domain.exception;

import com.nhnacademy.insightonruleengine.common.exception.EngineException;
import com.nhnacademy.insightonruleengine.common.exception.ErrorCode;

public class InvalidAiDraftNameException extends EngineException {

    public InvalidAiDraftNameException(String requiredPrefix, String name) {
        super(
                ErrorCode.FLOW_INVALID_AI_DRAFT_NAME,
                "AI draft Flow 이름은 \"%s\"로 시작해야 합니다: name=%s".formatted(requiredPrefix, name)
        );
    }
}
