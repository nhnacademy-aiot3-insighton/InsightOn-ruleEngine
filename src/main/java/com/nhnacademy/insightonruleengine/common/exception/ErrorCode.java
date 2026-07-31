package com.nhnacademy.insightonruleengine.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드와 HTTP 상태를 함께 갖는다. 새 예외를 추가할 때마다 여기 값을 하나씩 늘리면 된다.
 */

public enum ErrorCode {

    // Flow
    FLOW_NOT_FOUND(HttpStatus.NOT_FOUND),
    LINK_NOT_FOUND(HttpStatus.NOT_FOUND),
    NODE_NOT_FOUND(HttpStatus.NOT_FOUND),
    FLOW_DUPLICATE_NAME(HttpStatus.CONFLICT),
    FLOW_DELETION_NOT_ALLOWED(HttpStatus.CONFLICT),
    FLOW_INVALID_QUERY(HttpStatus.BAD_REQUEST),
    FLOW_INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT),
    FLOW_FORBIDDEN(HttpStatus.FORBIDDEN),
    FLOW_CYCLE_DETECTED(HttpStatus.BAD_REQUEST),
    FLOW_INVALID_LINK_PORT(HttpStatus.BAD_REQUEST),
    FLOW_INVALID_DEFINITION(HttpStatus.INTERNAL_SERVER_ERROR),
    FLOW_DUPLICATE_LINK_BINDING(HttpStatus.BAD_REQUEST),
    FLOW_INVALID_TRIGGER_COUNT(HttpStatus.BAD_REQUEST),
    FLOW_MISSING_ACTION_NODE(HttpStatus.BAD_REQUEST),
    FLOW_FAN_IN_NOT_ALLOWED(HttpStatus.BAD_REQUEST),
    FLOW_NOT_ACTIVE(HttpStatus.CONFLICT),
    FLOW_UNKNOWN_NODE_REFERENCE(HttpStatus.BAD_REQUEST);

    // Node 와 link 다른 도메인은 아직 미구현

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return name();
    }
}
