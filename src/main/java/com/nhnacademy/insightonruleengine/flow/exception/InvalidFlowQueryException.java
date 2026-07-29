package com.nhnacademy.insightonruleengine.flow.exception;

import com.nhnacademy.insightonruleengine.common.exception.EngineException;
import com.nhnacademy.insightonruleengine.common.exception.ErrorCode;

public class InvalidFlowQueryException extends EngineException {

    // locationId만 입력했을때 사용자에게 삐빅 에러처리
    public InvalidFlowQueryException() {
        super(ErrorCode.FLOW_INVALID_QUERY, "locationId를 조회 조건으로 사용하려면 status도 함께 입력해야 합니다.");
    }
}
