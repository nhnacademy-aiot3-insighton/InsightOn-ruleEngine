package com.nhnacademy.insightonruleengine.common.exception;

import com.nhnacademy.insightonruleengine.flow.domain.exception.CoreDependencyException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.ForbiddenException;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class GlobalExceptionHandler {

    // engineException 처리
    @ExceptionHandler(EngineException.class)
    public ResponseEntity<ErrorResponse> handleEngineException(EngineException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        if (errorCode.getHttpStatus().is5xxServerError()) {
            log.error(
                    "엔진 요청 처리 실패. errorCode={}, status={}",
                    errorCode.getCode(),
                    errorCode.getHttpStatus().value(),
                    exception
            );
        }

        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(new ErrorResponse(errorCode.getHttpStatus().value(), exception.getMessage()));
    }

    // BAD_REQUEST = 400 사용자가 요청값을 잘못 입력했을때 사용
    // 검증, JSON, enum, 타입 변환에 실패했을때 내부 오류 대신 같은 400 응답을 반환
    // 말 그대로 요청 자체가 잘못됐을때 날리는 부분
    @ExceptionHandler({
            BindException.class,
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
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

    // Core의 잘못된 응답을 사용자 권한 부족과 구분해 외부 의존성 오류로 반환합니다.
    @ExceptionHandler(CoreDependencyException.class)
    public ResponseEntity<ErrorResponse> handleCoreDependency(CoreDependencyException exception) {
        log.error("Core 서비스 의존성 호출 실패. status={}", HttpStatus.BAD_GATEWAY.value(), exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse(HttpStatus.BAD_GATEWAY.value(), exception.getMessage()));
    }

    //내부 실행 데이터 오류가 500 응답으로 반환하게 합니다.
}
