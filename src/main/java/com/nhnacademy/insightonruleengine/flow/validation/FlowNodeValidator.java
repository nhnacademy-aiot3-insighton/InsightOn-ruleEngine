package com.nhnacademy.insightonruleengine.flow.validation;

import com.nhnacademy.insightonruleengine.flow.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.node.domain.NodeType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FlowNodeValidator {

    public NodeValidationResult validate(List<FlowNodeRequest> nodes) {
        List<FlowStructureValidationError> errors = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            addError(
                    errors,
                    FlowStructureErrorCode.EMPTY_NODES,
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
                        FlowStructureErrorCode.EMPTY_NODES,
                        null,
                        fieldPath,
                        "노드는 필수입니다."
                );
                canValidateConnections = false;
                continue;
            }
            NodeFieldValidation fieldValidation = validateNodeField(node, fieldPath, errors);
            if (!fieldValidation.hasNodeType()) {
                canValidateConnections = false;
            }
            if (!fieldValidation.hasClientNodeKey()) {
                canValidateConnections = false;
            }
            String nodeKey = node.clientNodeKey();
            if (nodesByKey.containsKey(nodeKey)) {
                addError(
                        errors,
                        FlowStructureErrorCode.DUPLICATE_CLIENT_NODE_KEY,
                        nodeKey,
                        fieldPath + ".clientNodeKey",
                        "clientKey는 중복될 수 없습니다."
                );
                canValidateConnections = false;
                continue;
            }
            nodesByKey.put(nodeKey, node);
        }
        return new NodeValidationResult(
                Collections.unmodifiableMap(new LinkedHashMap<>(nodesByKey)),
                canValidateConnections,
                List.copyOf(errors)
        );
    }

    //누락된 노드 필드를 각각 기록해 사용자가 고칠 위치와 원인을 보여줍니다.
    private NodeFieldValidation validateNodeField(
            FlowNodeRequest node,
            String fieldPath,
            List<FlowStructureValidationError> errors
    ) {
        boolean hasClientNodeKey = node.clientNodeKey() != null
                && !node.clientNodeKey().isBlank();
        if (!hasClientNodeKey) {
            addError(
                    errors,
                    FlowStructureErrorCode.MISSING_CLIENT_NODE_KEY,
                    null,
                    fieldPath + ".clientNodeKey",
                    "clientNodeKey는 필수입니다."
            );
        }
        boolean hasNodeType = node.nodeType() != null;
        if (!hasNodeType) {
            addError(
                    errors,
                    FlowStructureErrorCode.MISSING_NODE_TYPE,
                    null,
                    fieldPath + ".nodeType",
                    "nodeType은 필수입니다."
            );
        }
        return new NodeFieldValidation(hasClientNodeKey, hasNodeType);
    }

    //하나의 시작점과 하나의 종착점이 있는지 확인합니다.
    public NodeRoleValidationResult validateRoles(NodeValidationResult result) {
        if (!result.canValidateConnections()) {
            return new NodeRoleValidationResult(false, List.of());
        }
        List<FlowStructureValidationError> errors = new ArrayList<>();
        long triggerCount = countCategory(result.nodesByKey().values(), NodeType.Category.TRIGGER);
        long actionCount = countCategory(result.nodesByKey().values(), NodeType.Category.ACTION);
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
    private long countCategory(Collection<FlowNodeRequest> nodes, NodeType.Category category) {
        return nodes.stream()
                .filter(node -> node.nodeType() != null && node.nodeType().getCategory() == category)
                .count();
    }

    //오류 생성 규칙을 모아 노드의 검증 단계를 정해준다.
    private void addError(
            List<FlowStructureValidationError> errors,
            FlowStructureErrorCode code,
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

    public record NodeRoleValidationResult(
            boolean hasValidNodeRoles,
            List<FlowStructureValidationError> errors
    ) {
    }

    private record NodeFieldValidation(
            boolean hasClientNodeKey,
            boolean hasNodeType
    ) {
    }
}
