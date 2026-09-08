package com.nhnacademy.insightonruleengine.flow.application.validation;

import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowValidationErrorReason;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.NodeErrorCode;
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
            if (!validateAndIndexNode(nodes.get(i), i, nodesByKey, errors)) {
                canValidateConnections = false;
            }
        }
        return new NodeValidationResult(
                Collections.unmodifiableMap(new LinkedHashMap<>(nodesByKey)),
                canValidateConnections,
                List.copyOf(errors)
        );
    }

    private boolean validateAndIndexNode(
            FlowNodeRequest node,
            int requestIndex,
            Map<String, FlowNodeRequest> nodesByKey,
            List<FlowStructureValidationError> errors
    ) {
        String fieldPath = "nodes[" + requestIndex + "]";
        if (node == null) {
            addError(errors, NodeErrorCode.NULL_NODE, null, fieldPath, "노드는 null일 수 없습니다.");
            return false;
        }

        NodeFieldValidation fieldValidation = validateNodeRequiredFields(node, fieldPath, errors);
        if (!fieldValidation.hasClientNodeKey()) {
            return false;
        }

        String nodeKey = node.clientNodeKey();
        boolean duplicateNodeKey = nodesByKey.putIfAbsent(nodeKey, node) != null;
        if (duplicateNodeKey) {
            addError(
                    errors,
                    NodeErrorCode.DUPLICATE_CLIENT_NODE_KEY,
                    nodeKey,
                    fieldPath + ".clientNodeKey",
                    "clientKey는 중복될 수 없습니다."
            );
        }
        return fieldValidation.hasNodeType() && !duplicateNodeKey;
    }

    //누락된 노드 필드를 각각 기록해 사용자가 고칠 위치와 원인을 보여줍니다.
    // Bean Validation 전에 직접 생성된 요청도 이 검증기를 거칠 수 있어 null 검사를 유지합니다.
    @SuppressWarnings({"java:S2583", "java:S2589"})
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
