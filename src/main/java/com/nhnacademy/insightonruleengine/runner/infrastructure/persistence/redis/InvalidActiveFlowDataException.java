package com.nhnacademy.insightonruleengine.runner.redis;

import com.nhnacademy.insightonruleengine.common.exception.EngineException;
import com.nhnacademy.insightonruleengine.common.exception.ErrorCode;

//Redis에서 읽은 Flow 데이터가 깨졌거나 요청한 Flow와 다를때 발생
public class InvalidActiveFlowDataException extends EngineException {
    //Json 변환 실패나 저장 데이터 검증에 실패했을때 사용합니다.
    public InvalidActiveFlowDataException(String message, Throwable cause) {
        super(ErrorCode.RUNTIME_INVALID_ACTIVE_FLOW_DATA, message, cause);
    }

    //Redis key와 Json 안의 ID가 다를때 사용합니다.
    public InvalidActiveFlowDataException(String message) {
        super(ErrorCode.RUNTIME_INVALID_ACTIVE_FLOW_DATA, message);
    }
}
