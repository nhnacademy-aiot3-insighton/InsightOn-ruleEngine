package com.nhnacademy.insightonruleengine.flow.domain.exception;

import com.nhnacademy.insightonruleengine.common.exception.EngineException;
import com.nhnacademy.insightonruleengine.common.exception.ErrorCode;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureValidationError;
import java.util.List;

// 규칙 기반 플로우 구성에 기본 구조 오류가 있을 때 발생.
public class InvalidFlowStructureException extends EngineException {

    private final List<FlowStructureValidationError> errors;

    // 구조 오류 목록을 잃지 않고 공통 API 예외로 전달합니다.
    public InvalidFlowStructureException(List<FlowStructureValidationError> errors) {
        super(ErrorCode.FLOW_INVALID_STRUCTURE, createMessage(errors));
        this.errors = List.copyOf(errors);
    }

    // 호출자가 검증 결과를 바꾸지 못하도록 불변 목록을 반환합니다.
    public List<FlowStructureValidationError> getErrors() {
        return errors;
    }

    // 첫 오류와 나머지 오류 개수로 사용자가 이해할 수 있는 메시지를 만듭니다.
    private static String createMessage(List<FlowStructureValidationError> errors) {
        validateErrors(errors);
        FlowStructureValidationError firstError = errors.getFirst();
        if (errors.size() == 1) {
            return "플로우 연결 구조가 올바르지 않습니다: " + firstError.message();
        }
        return "플로우 연결 구조가 올바르지 않습니다: %s 외 %d건의 오류가 있습니다."
                .formatted(firstError.message(), errors.size() - 1);
    }

    // 세부 원인이 없는 구조 예외가 만들어지는 것을 막습니다.
    private static void validateErrors(List<FlowStructureValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("플로우 구조 검증 오류 목록은 하나 이상이어야 합니다.");
        }
        for (FlowStructureValidationError error : errors) {
            if (error == null) {
                throw new IllegalArgumentException("플로우 구조 검증 오류는 null일 수 없습니다.");
            }
        }
    }
}
