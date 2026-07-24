package com.nhnacademy.insightonruleengine.flow.exception;

public class InvalidFlowQueryException extends RuntimeException {

    // locationId만 입력했을때 사용자에게 삐빅 에러처리
    public InvalidFlowQueryException() {
        super("locationId를 조회 조건으로 사용하려면 status도 함께 입력해야 합니다.");
    }
}
