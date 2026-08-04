package com.nhnacademy.insightonruleengine.flow.validation;

import com.nhnacademy.insightonruleengine.flow.domain.NodeType.Category;
import com.nhnacademy.insightonruleengine.flow.dto.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.validation.FlowNodeValidator.NodeValidationResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

//link를 검증, 노드와 연결 되는 부분을 검증
@Component
public class FlowLinkValidator {

    //링크 필수값과 노드와의 연결을 확인해 후속 규칙에 사용될만한 연결을 모아줍니다.
    public LinkValidationResult validate(
            List<FlowLinkRequest> links,
            Map<String, FlowNodeRequest> nodeByKey
    ) {
        List<FlowStructureValidationError> errors = new ArrayList<>();
        if (links == null || links.isEmpty()) {
            addError(
                    errors,
                    FlowStructureErrorCode.EMPTY_LINKS,
                    null,
                    "links",
                    "링크는 필수입니다."
            );
            return new LinkValidationResult(List.of(), false, List.copyOf(errors));
        }
        List<IndexedLink> indexedLinks = new ArrayList<>();
        boolean canValidateConnections = true;

        for (int i = 0; i < links.size(); i++) {
            FlowLinkRequest link = links.get(i);
            String fieldPath = "links[" + i + "]";
            if (link == null) {
                addError(
                        errors,
                        FlowStructureErrorCode.EMPTY_LINKS,
                        null,
                        fieldPath,
                        "링크는 null일 수 없습니다."
                );
                canValidateConnections = false;
                continue;
            }
            if (!validateLinkFields(link, fieldPath, errors)) {
                canValidateConnections = false;
                continue;
            }
            if (!validateLinkReferences(link, fieldPath, nodeByKey, errors)) {
                canValidateConnections = false;
                continue;
            }
            indexedLinks.add(new IndexedLink(i, link));
        }
        return new LinkValidationResult(
                List.copyOf(indexedLinks),
                canValidateConnections,
                List.copyOf(errors));
    }

    //누락된 링크 필드를 모두 기록한 뒤 검증이 가능한지, 실행이 가능한지 알려줍니다.
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
        return hasAllRequiredFields;
    }

    // 소스 포트와 타겟 포트가 각 노드에 실제로 존재하는지 확인합니다.
    // 둘 중 하나라도 존재하지 않으면 유효하지 않은 링크로 처리합니다.
    private boolean validateLinkReferences(
            FlowLinkRequest link,
            String fieldPath,
            Map<String, FlowNodeRequest> nodeByKey,
            List<FlowStructureValidationError> errors
    ) {
        boolean hasAllReferences = true;
        if (!nodeByKey.containsKey(link.sourceClientNodeKey())) {
            addError(
                    errors,
                    FlowStructureErrorCode.MISSING_SOURCE_NODE,
                    link.sourceClientNodeKey(),
                    fieldPath + ".sourceClientNodeKey",
                    "링크에 지정된 소스 노드 ID와 일치하는 노드가 없습니다."
            );
            hasAllReferences = false;
        }
        if (!nodeByKey.containsKey(link.targetClientNodeKey())) {
            addError(
                    errors,
                    FlowStructureErrorCode.MISSING_TARGET_NODE,
                    link.targetClientNodeKey(),
                    fieldPath + ".targetClientNodeKey",
                    "링크에 지정된 타겟 노드 ID와 일치하는 노드가 없습니다."
            );
            hasAllReferences = false;
        }
        return hasAllReferences;
    }

    //존재하는 노드끼리 연결된 링크의 중복 여부, 연결 방향과 포트 규칙을 검사한다.
    public LinkRulesResult validateLinkRules(
            LinkValidationResult linkResult,
            Map<String, FlowNodeRequest> nodeByKey
    ) {
        List<FlowStructureValidationError> errors = new ArrayList<>();
        Set<SourcePortKey> sourcePort = new LinkedHashSet<>();
        Set<String> sourceNodeKeys = new LinkedHashSet<>();
        boolean canValidateConnections = true;

        for (IndexedLink indexedLink : linkResult.indexedLinks()) {
            FlowLinkRequest link = indexedLink.link();
            String fieldPath = "links[" + indexedLink.requestIndex() + "]";
            sourceNodeKeys.add(link.sourceClientNodeKey());

            if (link.sourceClientNodeKey().equals(link.targetClientNodeKey())) {
                addError(
                        errors,
                        FlowStructureErrorCode.SELF_LOOP,
                        link.sourceClientNodeKey(),
                        fieldPath,
                        "노드는 자기 자신과 연결 할 수 없습니다."
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
                        "소스 노드 포트는 하나의 링크에서만 사용 가능합니다."
                );
                canValidateConnections = false;
            } else {
                sourcePort.add(sourcePortKey);
            }
            FlowNodeRequest source = nodeByKey.get(link.sourceClientNodeKey());
            FlowNodeRequest target = nodeByKey.get(link.targetClientNodeKey());
            if (!validatePorts(link, source, fieldPath, errors) || !validateDirection(link, source, target, fieldPath,
                    errors)) {
                canValidateConnections = false;
            }
        }
        return new LinkRulesResult(Set.copyOf(sourceNodeKeys), canValidateConnections, List.copyOf(errors));
    }

    //요청 순서를 유지한 채 IndexedLink에서 실제 링크만 추출합니다.
    public List<FlowLinkRequest> extractLinks(LinkValidationResult linkResult) {
        List<FlowLinkRequest> links = new ArrayList<>();
        for (IndexedLink indexedLink : linkResult.indexedLinks()) {
            links.add(indexedLink.link());
        }
        return List.copyOf(links);
    }

    //액션 출력과 트리거 입력에 대한 규칙을 검증합니다.
    private boolean validateDirection(
            FlowLinkRequest link,
            FlowNodeRequest source,
            FlowNodeRequest target,
            String fieldPath,
            List<FlowStructureValidationError> errors
    ) {
        boolean valid = true;
        if (source.nodeType().getCategory() == Category.ACTION) {
            addError(
                    errors,
                    FlowStructureErrorCode.ACTION_OUTPUT_LINK,
                    link.sourceClientNodeKey(),
                    fieldPath + ".sourceClientNodeKey",
                    "액션 노드는 종착점이므로 출력 링크를 가질 수 없습니다."
            );
            valid = false;
        }
        if (target.nodeType() != null
                && source.nodeType().getCategory() == Category.TRIGGER) {
            addError(
                    errors,
                    FlowStructureErrorCode.TRIGGER_INPUT_LINK,
                    link.targetClientNodeKey(),
                    fieldPath + ".targetClientNodeKey",
                    "트리거 노드는 시작점이므로 입력 링크를 가질 수 없습니다."
            );
            valid = false;
        }
        return valid;
    }

    //소스포트와 타겟포트의 계약을 검증합니다 소스포트는 타입 검증, 타겟포트는 in 이어야만 허용.
    private boolean validatePorts(
            FlowLinkRequest link,
            FlowNodeRequest node,
            String fieldPath,
            List<FlowStructureValidationError> errors
    ) {
        boolean valid = node.nodeType() != null;
        if (valid
                && !node.nodeType().getPortSchema().outputPorts(null).contains(link.sourcePort())) {
            addError(
                    errors,
                    FlowStructureErrorCode.INVALID_PORT,
                    link.sourceClientNodeKey(),
                    fieldPath + ".sourcePort",
                    "소스포트와 노드타입이 일치하지 않습니다."
            );
            valid = false;
        }
        if (!"in".equals(link.targetPort())) {
            addError(
                    errors,
                    FlowStructureErrorCode.INVALID_PORT,
                    link.targetClientNodeKey(),
                    fieldPath + ".targetPort",
                    "타겟 포트는 in만 사용할 수 있습니다."
            );
            valid = false;
        }
        return valid;
    }

    //액션이 아닌 노드에 출력 링크가 있는지 확인하는 검증
    public List<FlowStructureValidationError> validateMissingOutputLinks(
            NodeValidationResult nodeResult,
            LinkValidationResult linkResult,
            Set<String> sourceNodeKeys
    ) {
        if (!nodeResult.canValidateConnections() || !linkResult.canValidateConnections()) {
            return List.of();
        }
        List<FlowStructureValidationError> errors = new ArrayList<>();
        for (FlowNodeRequest node : nodeResult.nodesByKey().values()) {
            if (node.nodeType().getCategory() != Category.ACTION
                    && !sourceNodeKeys.contains(node.clientNodeKey())) {
                addError(
                        errors,
                        FlowStructureErrorCode.MISSING_OUTPUT_LINK,
                        node.clientNodeKey(),
                        "nodes",
                        "액션이 아닌 노드는 출력 링크를 1 이상 가져야 합니다."
                );
            }
        }
        return List.copyOf(errors);
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

    public record LinkValidationResult(
            List<IndexedLink> indexedLinks,
            boolean canValidateConnections,
            List<FlowStructureValidationError> errors
    ) {
    }

    public record IndexedLink(
            int requestIndex,
            FlowLinkRequest link
    ) {
    }

    public record LinkRulesResult(
            // Set 사용 이유: Set.contains(key): O(1) > List.contains(key): O(n)
            // 중복 없는 source node의 존재 여부를 조회하면서 큰 성능 차이는 아니지만 Set이 더 조회하기 빠름
            Set<String> sourceNodeKey,
            boolean canValidateConnections,
            List<FlowStructureValidationError> errors
    ) {
    }

    private record SourcePortKey(
            String sourceClientNodeKey,
            String sourcePort
    ) {
    }
}
