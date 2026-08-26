package com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis;

import com.nhnacademy.insightonruleengine.common.exception.EngineException;
import com.nhnacademy.insightonruleengine.common.exception.ErrorCode;

//Redis Route 목록에 정상적이지 않은 Flow Id가 들어 있을 때 발생
public class InvalidRouteDataException extends EngineException {
    //Redis 연결 문제가 아닌 저장한 값 자체의 문제
    public InvalidRouteDataException(String message, Throwable cause) {
        super(ErrorCode.RUNTIME_INVALID_ROUTE_DATA, message, cause);
    }
}
