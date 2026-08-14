package com.nhnacademy.insightonruleengine.flow.validation;

import com.nhnacademy.insightonruleengine.flow.domain.NodeType.Category;
import com.nhnacademy.insightonruleengine.flow.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.validation.NodeValidator.NodeValidationResult;
import com.nhnacademy.insightonruleengine.flow.validation.domain.FlowStructureErrorCode;
import com.nhnacademy.insightonruleengine.flow.validation.domain.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.validation.domain.FlowValidationErrorReason;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FlowNodeValidator {

    //하나의 시작점과 하나의 종착점이 있는지 확인합니다.
    public NodeRoleValidationResult validateRoles(NodeValidationResult result) {
        if (!result.canValidateConnections()) {
            return new NodeRoleValidationResult(false, List.of());
        }
        List<FlowStructureValidationError> errors = new ArrayList<>();
        long triggerCount = countCategory(result.nodesByKey().values(), Category.TRIGGER);
        long actionCount = countCategory(result.nodesByKey().values(), Category.ACTION);
        boolean hasValidRoles = true;
        if (triggerCount != 1L) {
            addError(
                    errors,
                    FlowStructureErrorCode.INVALID_TRIGGER_COUNT,
                    null,
                    "nodes",
                    "Trigger Node는 정확히 하나여야 합니다."
            );
            hasValidRoles = false;
        }
        if (actionCount == 0L) {
            addError(
                    errors,
                    FlowStructureErrorCode.MISSING_ACTION,
                    null,
                    "nodes",
                    "Action Node는 하나 이상이어야 합니다."
            );
            hasValidRoles = false;
        }
        return new NodeRoleValidationResult(hasValidRoles, List.copyOf(errors));
    }

    //null 노드타입 제외, 노드 수를 카운트한다.
    private long countCategory(Collection<FlowNodeRequest> nodes, Category category) {
        return nodes.stream()
                .filter(node -> node.nodeType() != null && node.nodeType().getCategory() == category)
                .count();
    }

    //오류 생성 규칙을 모아 노드의 검증 단계를 정해준다.
    private void addError(
            List<FlowStructureValidationError> errors,
            FlowValidationErrorReason code,
            String clientNodeKey,
            String fieldPath,
            String message
    ) {
        errors.add(new FlowStructureValidationError(code, clientNodeKey, fieldPath, message));
    }

    public record NodeRoleValidationResult(
            boolean hasValidNodeRoles,
            List<FlowStructureValidationError> errors
    ) {
    }
}
