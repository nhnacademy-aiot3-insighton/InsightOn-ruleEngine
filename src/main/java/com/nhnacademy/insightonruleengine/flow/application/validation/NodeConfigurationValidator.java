package com.nhnacademy.insightonruleengine.flow.application.validation;

import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.NodeErrorCode;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

//저장할 Node configuration을 실행 경로와 같은 타입 모델로 변환해 검증합니다.
@Component
@RequiredArgsConstructor
public class NodeConfigurationValidator {

    private static final String DEFAULT_ERROR_MESSAGE = "Node configuration이 올바르지 않습니다.";

    private final NodeParamsParser nodeParamsParser;

    public List<FlowStructureValidationError> validate(List<FlowNodeRequest> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        List<FlowStructureValidationError> errors = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++) {
            FlowNodeRequest node = nodes.get(index);
            if (!canValidate(node)) {
                continue;
            }
            validateNode(node, index, errors);
        }
        return List.copyOf(errors);
    }

    private boolean canValidate(FlowNodeRequest node) {
        return node != null
                && node.nodeType() != null
                && node.configuration() != null
                && !node.configuration().isNull();
    }

    private void validateNode(
            FlowNodeRequest node,
            int index,
            List<FlowStructureValidationError> errors
    ) {
        try {
            nodeParamsParser.parse(node.nodeType(), node.configuration());
        } catch (ConstraintViolationException e) {
            addConstraintErrors(node, index, e, errors);
        } catch (IllegalArgumentException e) {
            errors.add(error(node, "nodes[" + index + "].configuration", errorMessage(e)));
        }
    }

    private void addConstraintErrors(
            FlowNodeRequest node,
            int index,
            ConstraintViolationException exception,
            List<FlowStructureValidationError> errors
    ) {
        exception.getConstraintViolations().stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(violation -> error(node, fieldPath(index, violation), violation.getMessage()))
                .forEach(errors::add);
    }

    private String fieldPath(int index, ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        String configurationPath = "nodes[" + index + "].configuration";
        if (propertyPath.isBlank()) {
            return configurationPath;
        }
        return configurationPath + "." + propertyPath;
    }

    private FlowStructureValidationError error(
            FlowNodeRequest node,
            String fieldPath,
            String message
    ) {
        return new FlowStructureValidationError(
                NodeErrorCode.INVALID_NODE_CONFIGURATION,
                node.clientNodeKey(),
                fieldPath,
                message
        );
    }

    private String errorMessage(IllegalArgumentException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return DEFAULT_ERROR_MESSAGE;
        }
        return exception.getMessage();
    }
}
