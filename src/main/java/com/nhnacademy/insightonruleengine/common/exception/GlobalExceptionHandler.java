package com.nhnacademy.insightonruleengine.common.exception;

import com.nhnacademy.insightonruleengine.flow.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowDeletionNotAllowedException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowNotFoundException;
import com.nhnacademy.insightonruleengine.flow.exception.ForbiddenException;
import com.nhnacademy.insightonruleengine.flow.exception.InvalidFlowQueryException;
import com.nhnacademy.insightonruleengine.flow.exception.InvalidFlowStatusTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // NOT_FOUND = 404 요청한 플로우를 찾을 수 없을때 사용
    // 플로우 존재 여부와 그룹 소유권이 아닌 경우에 404를 띄워 다른 그룹(회사) 다른 그룹(회사)의 플로우 정보 노출을 방지
    @ExceptionHandler(FlowNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFlowNotFound(FlowNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), exception.getMessage()));
    }

    // CONFLICT = 409 사용자의 요청과 서버의 규칙이 충돌할때 사용
    @ExceptionHandler({
            // 같은 이름의 플로우는 존재 할 수 없음
            DuplicateFlowNameException.class,
            // 휴지통(ARCHIVE)에 있는 플로우만 삭제 가능
            FlowDeletionNotAllowedException.class,
            // 플로우 상태 변경은 active, inactive만 가능합니다.
            InvalidFlowStatusTransitionException.class
    })
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.value(), exception.getMessage()));
    }

    // BAD_REQUEST = 400 사용자가 요청값을 잘못 입력했을때 사용
    // locationId만 입력했을때 사용자에게 삐빅 에러처리
    @ExceptionHandler(InvalidFlowQueryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQuery(InvalidFlowQueryException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), exception.getMessage()));
    }

    // BAD_REQUEST = 400 사용자가 요청값을 잘못 입력했을때 사용
    // 검증, JSON, enum, 타입 변환에 실패했을때 내부 오류 대신 같은 400 응답을 반환
    // 말 그대로 요청 자체가 잘못됐을때 날리는 부분
    @ExceptionHandler({
            BindException.class,
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "요청값이 올바르지 않습니다."));
    }

    //권한 실패를 안정적인 공통 403 응답
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(
                        HttpStatus.FORBIDDEN.value(),
                        exception.getMessage()
                ));
    }
}
