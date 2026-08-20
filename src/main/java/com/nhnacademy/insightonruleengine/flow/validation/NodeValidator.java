package com.nhnacademy.insightonruleengine.flow.validation;

import com.nhnacademy.insightonruleengine.flow.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.validation.domain.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.validation.domain.FlowValidationErrorReason;
import com.nhnacademy.insightonruleengine.flow.validation.domain.NodeErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

//단일 노드 필수값 및 유효성을 검사합니다.
@Component
public class NodeValidator {

    //필수 노드값을 검사하고 링크와 연결될 노드를 key로 저장합니다.
    public NodeValidationResult validate(List<FlowNodeRequest> nodes) {
        List<FlowStructureValidationError> errors = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            addError(
                    errors,
                    NodeErrorCode.EMPTY_NODES,
                    null,
                    "nodes",
                    "노드는 필수입니다."
            );
            return new NodeValidationResult(Map.of(), false, List.copyOf(errors));
        }
        Map<String, FlowNodeRequest> nodesByKey = new LinkedHashMap<>();
        boolean canValidateConnections = true;
        for (int i = 0; i < nodes.size(); i++) {
            FlowNodeRequest node = nodes.get(i);
            String fieldPath = "nodes[" + i + "]";
            if (node == null) {
                addError(
                        errors,
                        NodeErrorCode.NULL_NODE,
                        null,
                        fieldPath,
                        "노드는 null일 수 없습니다."
                );
                canValidateConnections = false;
            } else {
                NodeFieldValidation fieldValidation = validateNodeRequiredFields(node, fieldPath, errors);
                if (!fieldValidation.hasNodeType() || !fieldValidation.hasClientNodeKey()) {
                    canValidateConnections = false;
                }
                if (fieldValidation.hasClientNodeKey()) {
                    String nodeKey = node.clientNodeKey();
                    if (nodesByKey.putIfAbsent(nodeKey, node) != null) {
                        addError(
                                errors,
                                NodeErrorCode.DUPLICATE_CLIENT_NODE_KEY,
                                nodeKey,
                                fieldPath + ".clientNodeKey",
                                "clientKey는 중복될 수 없습니다."
                        );
                        canValidateConnections = false;
                    }
                }
            }
        }
        return new NodeValidationResult(
                Collections.unmodifiableMap(new LinkedHashMap<>(nodesByKey)),
                canValidateConnections,
                List.copyOf(errors)
        );
    }

    //누락된 노드 필드를 각각 기록해 사용자가 고칠 위치와 원인을 보여줍니다.
    public NodeFieldValidation validateNodeRequiredFields(
            FlowNodeRequest node,
            String fieldPath,
            List<FlowStructureValidationError> errors
    ) {
        boolean hasClientNodeKey = node.clientNodeKey() != null
                && !node.clientNodeKey().isBlank();
        if (!hasClientNodeKey) {
            addError(
                    errors,
                    NodeErrorCode.MISSING_CLIENT_NODE_KEY,
                    null,
                    fieldPath + ".clientNodeKey",
                    "clientNodeKey는 필수입니다."
            );
        }
        boolean hasNodeType = node.nodeType() != null;
        if (!hasNodeType) {
            addError(
                    errors,
                    NodeErrorCode.MISSING_NODE_TYPE,
                    null,
                    fieldPath + ".nodeType",
                    "nodeType은 필수입니다."
            );
        }
        if (node.configuration() == null || node.configuration().isNull()) {
            addError(
                    errors,
                    NodeErrorCode.MISSING_NODE_CONFIGURATION,
                    node.clientNodeKey(),
                    fieldPath + ".configuration",
                    "configuration은 필수입니다."
            );
        }
        return new NodeFieldValidation(hasClientNodeKey, hasNodeType);
    }

    private void addError(
            List<FlowStructureValidationError> errors,
            FlowValidationErrorReason code,
            String clientNodeKey,
            String fieldPath,
            String message
    ) {
        errors.add(new FlowStructureValidationError(code, clientNodeKey, fieldPath, message));
    }

    public record NodeValidationResult(
            Map<String, FlowNodeRequest> nodesByKey,
            boolean canValidateConnections,
            List<FlowStructureValidationError> errors
    ) {
    }

    public record NodeFieldValidation(
            boolean hasClientNodeKey,
            boolean hasNodeType
    ) {
    }
}
