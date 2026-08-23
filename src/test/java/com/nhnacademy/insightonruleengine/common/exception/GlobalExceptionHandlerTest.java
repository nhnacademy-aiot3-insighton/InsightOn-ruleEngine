package com.nhnacademy.insightonruleengine.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.FlowNotFoundException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.InvalidFlowStatusTransitionException;
import com.nhnacademy.insightonruleengine.runner.redis.InvalidActiveFlowDataException;
import com.nhnacademy.insightonruleengine.runner.redis.InvalidRouteDataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("EngineException은 ErrorCode의 HTTP 상태로 응답한다")
    void handleEngineExceptionStatusTest() {
        assertEngineExceptionStatus(new FlowNotFoundException(1L, 10L), HttpStatus.NOT_FOUND);
        assertEngineExceptionStatus(new DuplicateFlowNameException(1L, 10L, "온도"), HttpStatus.CONFLICT);
        assertEngineExceptionStatus(
                new InvalidFlowStatusTransitionException(FlowStatus.ACTIVE, FlowStatus.ARCHIVED),
                HttpStatus.CONFLICT
        );
        assertEngineExceptionStatus(
                new InvalidActiveFlowDataException("잘못된 Active Flow 데이터"),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
        assertEngineExceptionStatus(
                new InvalidRouteDataException("잘못된 Route 데이터", new NumberFormatException()),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    private void assertEngineExceptionStatus(EngineException exception, HttpStatus expectedStatus) {
        ResponseEntity<ErrorResponse> response = handler.handleEngineException(exception);

        assertEquals(expectedStatus, response.getStatusCode());
        assertEquals(expectedStatus.value(), response.getBody().status());
        assertEquals(exception.getMessage(), response.getBody().message());
    }
}
