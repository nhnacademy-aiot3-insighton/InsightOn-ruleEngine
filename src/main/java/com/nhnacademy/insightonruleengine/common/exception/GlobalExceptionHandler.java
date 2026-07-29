package com.nhnacademy.insightonruleengine.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // engineException 처리
    @ExceptionHandler(EngineException.class)
    public ResponseEntity<ErrorResponse> handleEngineException(EngineException exception) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(new ErrorResponse(errorCode.getHttpStatus().value(), exception.getMessage()));
    }

    // BAD_REQUEST = 400 사용자가 요청값을 잘못 입력했을때 사용
    // 검증, JSON, enum, 타입 변환에 실패했을때 내부 오류 대신 같은 400 응답을 반환
    // 말 그대로 요청 자체가 잘못됐을때 날리는 부분
    @ExceptionHandler({
            BindException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "요청값이 올바르지 않습니다."));
    }
}
