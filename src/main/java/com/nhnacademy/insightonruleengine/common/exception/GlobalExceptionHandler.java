package com.nhnacademy.insightonruleengine.common.exception;

import com.nhnacademy.insightonruleengine.flow.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowDeletionNotAllowedException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowNotFoundException;
import com.nhnacademy.insightonruleengine.flow.exception.InvalidFlowQueryException;
import com.nhnacademy.insightonruleengine.flow.exception.InvalidFlowStatusTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 미존재와 그룹 불일치를 같은 404로 내려 다른 그룹의 Flow 정보를 숨긴다.
    @ExceptionHandler(FlowNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFlowNotFound(FlowNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), exception.getMessage()));
    }

    // 저장된 데이터와 충돌하는 이름·삭제·상태 규칙을 같은 409 계약으로 반환한다.
    @ExceptionHandler({
            DuplicateFlowNameException.class,
            FlowDeletionNotAllowedException.class,
            InvalidFlowStatusTransitionException.class
    })
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.value(), exception.getMessage()));
    }

    // 지원하지 않는 목록 Query 조합은 클라이언트가 고칠 수 있는 400 오류로 안내한다.
    @ExceptionHandler(InvalidFlowQueryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQuery(InvalidFlowQueryException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), exception.getMessage()));
    }

    // 검증·JSON·타입 변환 실패의 내부 내용을 숨기고 공통 400 응답으로 통일한다.
    @ExceptionHandler({
            BindException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "요청값이 올바르지 않습니다."));
    }
}
