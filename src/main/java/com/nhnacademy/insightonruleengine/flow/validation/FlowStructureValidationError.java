package com.nhnacademy.insightonruleengine.flow.validation;

public record FlowStructureValidationError(
        FlowStructureErrorCode code,
        String clientNodeKey,
        String fieldPath,
        String message
) {
    public FlowStructureValidationError {
        if (code == null) {
            throw new IllegalArgumentException("검증 오류 코드는 필수입니다.");
        }
        if (fieldPath == null || fieldPath.isBlank()) {
            throw new IllegalArgumentException("검증 오류 필드 경로는 필수입니다.");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("검증 오류 메시지는 필수입니다.");
        }
    }
}
