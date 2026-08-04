package com.nhnacademy.insightonruleengine.flow.validation;

import com.nhnacademy.insightonruleengine.flow.dto.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.node.domain.NodeType;
import com.nhnacademy.insightonruleengine.node.domain.NodeType.Category;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

//DB 저장 전 요청 검증
@Component
public class FlowStructureValidator {

    //기본 연결 규칙, 참조, 연결 탐색 순서로 검증 오류를 수집합니다. 전체적인 검증을 addAll로 구현
    public List<FlowStructureValidationError> validate(
            List<FlowNodeRequest> nodes,
            List<FlowLinkRequest> links
    ) {
        List<FlowStructureValidationError> errors = new ArrayList<>();

        NodeIndex nodeIndex = validateAndIndexNodes(nodes, errors);
        LinkIndex linkIndex = validateAndIndexLinks(links, nodeIndex.nodesByKey(), errors);

        boolean hasValidNodeRoles = validateNodeRoles(nodeIndex, errors);
        LinkRulesResult linkRules = validateLinkRules(
                linkIndex.validLinks,
                nodeIndex.nodesByKey(),
                errors
        );
        validateMissingOutputLinks(nodeIndex, linkIndex, linkRules.sourceNodeKey(), errors);

        boolean canValidateConnections = nodeIndex.canValidateConnections()
                && linkIndex.canValidateConnections()
                && linkRules.canValidateConnections()
                && hasValidNodeRoles;
        if (canValidateConnections) {
            // 수 목 금 구현 예정
        }
        return List.copyOf(errors);
    }

    //필수 노드 값을 검사하고 링크가 참조할 첫 노드를 키의 형태로 보존합니다.
    private NodeIndex validateAndIndexNodes(
            List<FlowNodeRequest> nodes,
            List<FlowStructureValidationError> errors
    ) {
        if (nodes == null || nodes.isEmpty()) {
            addError(
                    errors,
                    FlowStructureErrorCode.EMPTY_NODES,
                    null,
                    "nodes",
                    "노드는 필수입니다."
            );
            return new NodeIndex(Map.of(), false);
        }
        Map<String, FlowNodeRequest> nodeByKey = new LinkedHashMap<>();
        boolean canValidateConnections = true;
        for (int i = 0; i < nodes.size(); i++) {
            FlowNodeRequest request = nodes.get(i);
            String fieldPath = "nodes[" + i + "]";
            if (request == null) {
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
            NodeFieldValidation fieldValidation = validateNodeField(request, fieldPath, errors);
            if (!fieldValidation.hasNodeType()) {
                canValidateConnections = false;
            }
            if (!fieldValidation.hasClientNodeKey()) {
                canValidateConnections = false;
            }
            String nodeKey = request.clientNodeKey();
            if (nodeByKey.containsKey(nodeKey)) {
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
            nodeByKey.put(nodeKey, request);

        }
        return new NodeIndex(
                //절대 바뀌면 안되는 노드키 unmodifiableMap으로 감싸줌
                Collections.unmodifiableMap(new LinkedHashMap<>(nodeByKey)),
                canValidateConnections
        );
    }

    // 누락된 노드 필드를 각각 기록해 사용자가 고칠 위치와 원인을 보여줍니다.
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
        if (node.configuration() == null) {
            addError(
                    errors,
                    FlowStructureErrorCode.MISSING_NODE_CONFIGURATION,
                    null,
                    fieldPath + ".nodeConfiguration",
                    "nodeConfiguration은 필수입니다."
            );
        }
        return new NodeFieldValidation(hasClientNodeKey, hasNodeType);
    }

    // 하나의 시작점과 하나의 종착점이 있는지 확인합니다.
    private boolean validateNodeRoles(
            NodeIndex nodeIndex,
            List<FlowStructureValidationError> errors
    ) {
        if (!nodeIndex.canValidateConnections()) {
            return false;
        }

        long triggerCount = countCategory(nodeIndex.nodesByKey().values(), NodeType.Category.TRIGGER);
        long actionCount = countCategory(nodeIndex.nodesByKey().values(), NodeType.Category.ACTION);
        boolean hasValidRoles = true;
        if (triggerCount != 1L) {
            addError(errors, FlowStructureErrorCode.INVALID_TRIGGER_COUNT, null, "nodes",
                    "Trigger Node는 정확히 하나여야 합니다.");
            hasValidRoles = false;
        }
        if (actionCount == 0L) {
            addError(errors, FlowStructureErrorCode.MISSING_ACTION, null, "nodes",
                    "Action Node는 하나 이상이어야 합니다.");
            hasValidRoles = false;
        }
        return hasValidRoles;
    }

    //링크 필수값과 노드 참조를 확인하여 후속 규칙에 사용될 연결을 모아줍니다.
    private LinkIndex validateAndIndexLinks(
            List<FlowLinkRequest> links,
            Map<String, FlowNodeRequest> nodesByKey,
            List<FlowStructureValidationError> errors
    ) {
        if (links == null || links.isEmpty()) {
            addError(
                    errors,
                    FlowStructureErrorCode.EMPTY_LINKS,
                    null,
                    "links",
                    "링크는 필수입니다."
            );
            return new LinkIndex(List.of(), false);
        }
        List<IndexedLink> indexedLinks = new ArrayList<>();
        boolean canValidateConnections = true;
        for (int i = 0; i < links.size(); i++) {
            FlowLinkRequest request = links.get(i);
            String fieldPath = "links[" + i + "]";
            if (request == null) {
                addError(
                        errors,
                        FlowStructureErrorCode.EMPTY_LINKS,
                        null,
                        "links",
                        "링크는 필수입니다."
                );
                canValidateConnections = false;
                continue;
            }

        }
        return new LinkIndex(List.copyOf(indexedLinks), canValidateConnections);
    }

    //null 노드타입 제외, 노드 수를 카운트한다.
    private long countCategory(Collection<FlowNodeRequest> nodes, NodeType.Category category) {
        return nodes.stream()
                .filter(node -> node.nodeType() != null && node.nodeType().getCategory() == category)
                .count();
    }

    // 누락된 링크 필드를 모두 기록한 뒤 참조 검사가 가능한지 실행할 수 있는지 알려줍니다.
    private boolean validateLinkFields(
            FlowLinkRequest link,
            String fieldPath,
            List<FlowStructureValidationError> errors
    ) {
        boolean hasAllRequiredFields = true;
        if (link.sourceClientNodeKey() == null || link.sourceClientNodeKey().isBlank()) {
            addError(
                    errors,
                    FlowStructureErrorCode.MISSING_SOURCE_CLIENT_NODE_KEY,
                    null,
                    fieldPath + ".sourceClientNodeKey",
                    "sourceClientNodeKey는 필수입니다."
            );
            hasAllRequiredFields = false;
        }
        if (link.targetClientNodeKey() == null || link.targetClientNodeKey().isBlank()) {
            addError(
                    errors,
                    FlowStructureErrorCode.MISSING_TARGET_CLIENT_NODE_KEY,
                    null,
                    fieldPath + ".targetClientNodeKey",
                    "targetClientNodeKey는 필수입니다."
            );
            hasAllRequiredFields = false;
        }
        if (link.sourcePort() == null || link.sourcePort().isBlank()) {
            addError(
                    errors,
                    FlowStructureErrorCode.MISSING_SOURCE_PORT,
                    null,
                    fieldPath + ".sourcePort",
                    "sourcePort는 필수입니다."
            );
            hasAllRequiredFields = false;
        }
        if (link.targetPort() == null || link.targetPort().isBlank()) {
            addError(
                    errors,
                    FlowStructureErrorCode.MISSING_TARGET_PORT,
                    null,
                    fieldPath + ".targetPort",
                    "targetPortS는 필수입니다."
            );
            hasAllRequiredFields = false;
        }
        return hasAllRequiredFields;
    }

    // 소스 포트와 타겟 포트가 각 노드에 실제로 존재하는지 확인합니다.
    // 둘 중 하나라도 존재하지 않으면 유효하지 않은 링크로 처리합니다.
    private boolean validateLinkReferences(
            FlowLinkRequest request,
            String fieldPath,
            Map<String, FlowNodeRequest> nodesByKey,
            List<FlowStructureValidationError> errors
    ) {
        boolean isValid = true;
        return true;
    }

    private LinkRulesResult validateLinkRules(
            List<IndexedLink> indexedLinks,
            Map<String, FlowNodeRequest> nodeByKey,
            List<FlowStructureValidationError> errors
    ) {
        Set<SourcePortKey> sourcePort = new LinkedHashSet<>();
        Set<String> sourceNodeKeys = new LinkedHashSet<>();
        boolean canValidateConnections = true;

        for (IndexedLink indexedLink : indexedLinks) {
            FlowLinkRequest link = indexedLink.link();
            String fieldPath = "links[" + indexedLink.requestIndex() + "]";
            sourceNodeKeys.add(link.sourceClientNodeKey());
            if (link.sourceClientNodeKey().equals(link.targetClientNodeKey())) {
                addError(
                        errors,
                        FlowStructureErrorCode.SELF_LOOP,
                        link.sourceClientNodeKey(),
                        fieldPath,
                        "Node는 자기 자신과 연결할 수 없습니다."
                );
                canValidateConnections = false;
            }
            SourcePortKey sourcePortKey = new SourcePortKey(
                    link.sourceClientNodeKey(),
                    link.sourcePort()
            );
            if (sourcePort.contains(sourcePortKey)) {
                addError(
                        errors,
                        FlowStructureErrorCode.DUPLICATE_SOURCE_PORT,
                        link.sourceClientNodeKey(),
                        fieldPath + ".sourcePort",
                        "Source Node Port는 한 link에만 사용 가능합니다."
                );
                canValidateConnections = false;
            } else {
                sourcePort.add(sourcePortKey);
            }
            FlowNodeRequest source = nodeByKey.get(link.sourceClientNodeKey());
            FlowNodeRequest target = nodeByKey.get(link.targetClientNodeKey());
            if (!validateDirection(link, source, target, fieldPath, errors)) {
                canValidateConnections = false;
            }
            if (!validatePorts(link, source, fieldPath, errors)) {
                canValidateConnections = false;
            }
        }
        return new LinkRulesResult(Set.copyOf(sourceNodeKeys), canValidateConnections);
    }

    private boolean validateDirection(
            FlowLinkRequest link,
            FlowNodeRequest source,
            FlowNodeRequest target,
            String fieldPath,
            List<FlowStructureValidationError> errors
    ) {
        boolean isValid = true;
        if (source.nodeType() != null
                && source.nodeType().getCategory() == Category.ACTION
        ) {
            addError(
                    errors,
                    FlowStructureErrorCode.ACTION_OUTPUT_LINK,
                    link.sourceClientNodeKey(),
                    fieldPath + ".sourceClientKey",
                    "액션은 종착점이므로 나가는 링크가 없어야합니다."
            );
            isValid = false;
        }
        if (target.nodeType() != null
                && target.nodeType().getCategory() == Category.TRIGGER
        ) {
            addError(
                    errors,
                    FlowStructureErrorCode.TRIGGER_INPUT_LINK,
                    link.targetClientNodeKey(),
                    fieldPath + ".targetClientKey",
                    "트리거는 시작점이므로 받는 링크가 존재할 수 없습니다."
            );
            isValid = false;
        }
        return isValid;
    }

    private boolean validatePorts(
            FlowLinkRequest link,
            FlowNodeRequest node,
            String fieldPath,
            List<FlowStructureValidationError> errors
    ) {
        boolean isValid = node.nodeType() != null;
        if (isValid
                && !node.nodeType().getPortSchema().outputPorts(null).contains(link.sourcePort())) {
            addError(
                    errors,
                    FlowStructureErrorCode.INVALID_PORT,
                    link.sourceClientNodeKey(),
                    fieldPath + ".sourceClientKey",
                    "소스포트가 노드 타입과 맞지 않습니다."
            );
            isValid = false;
        }
        if (!"in".equals(link.targetPort())) {
            addError(
                    errors,
                    FlowStructureErrorCode.INVALID_PORT,
                    link.targetClientNodeKey(),
                    fieldPath + ".targetPort",
                    "Target 포트는 in만 허용합니다."
            );
            isValid = false;
        }
        return isValid;
    }

    //액션이 아닌 노드에 출력 링크가 있는지 확인하는 검증
    private void validateMissingOutputLinks(
            NodeIndex nodeIndex,
            LinkIndex linkIndex,
            Set<String> sourceNodeKeys,
            List<FlowStructureValidationError> errors
    ) {
        if (!nodeIndex.canValidateConnections() || !linkIndex.canValidateConnections()) {
            return;
        }
        for (FlowNodeRequest node : nodeIndex.nodesByKey().values()) {
            if (node.nodeType().getCategory() != NodeType.Category.ACTION
                    && !sourceNodeKeys.contains(node.clientNodeKey())) {
                addError(
                        errors,
                        FlowStructureErrorCode.MISSING_OUTPUT_LINK,
                        node.clientNodeKey(),
                        "nodes",
                        "Action이 아닌 노드는 출력 링크가 하나 이상 가져야 합니다."
                );
            }
        }
    }

    private void addError(
            List<FlowStructureValidationError> errors,
            FlowStructureErrorCode code,
            String clientNodeKey,
            String fieldPath,
            String message
    ) {
        errors.add(new FlowStructureValidationError(code, clientNodeKey, fieldPath, message));
    }

    private record NodeIndex(
            Map<String, FlowNodeRequest> nodesByKey,
            boolean canValidateConnections
    ) {
    }

    private record NodeFieldValidation(
            boolean hasClientNodeKey,
            boolean hasNodeType
    ) {
    }

    private record LinkIndex(
            List<IndexedLink> validLinks,
            boolean canValidateConnections
    ) {
    }

    private record IndexedLink(
            int requestIndex,
            FlowLinkRequest link
    ) {
    }

    private record LinkRulesResult(
            // Set 사용 이유: Set.contains(key): O(1) > List.contains(key): O(n)
            // 중복 없는 source node의 존재 여부를 조회하면서 큰 성능 차이는 아니지만 Set이 더 조회하기 빠름
            Set<String> sourceNodeKey,
            boolean canValidateConnections
    ) {
    }

    private record SourcePortKey(
            String sourceClientNodeKey,
            String sourcePort
    ) {
    }
}
