package com.nhnacademy.insightonruleengine.flow.application.validation;

import com.nhnacademy.insightonruleengine.flow.domain.NodeType.Category;
import com.nhnacademy.insightonruleengine.flow.api.dto.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.application.validation.LinkValidator.IndexedLink;
import com.nhnacademy.insightonruleengine.flow.application.validation.LinkValidator.LinkValidationResult;
import com.nhnacademy.insightonruleengine.flow.application.validation.NodeValidator.NodeValidationResult;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStructureErrorCode;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.domain.FlowValidationErrorReason;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

//link를 검증, 노드와 연결 되는 부분을 검증
@Component
public class FlowLinkValidator {

    // 소스 포트와 타겟 포트가 각 노드에 실제로 존재하는지 확인합니다.
    // 둘 중 하나라도 존재하지 않으면 유효하지 않은 링크로 처리합니다.
    public LinkReferenceResult validateLinkReferences(
            LinkValidationResult linkResult,
            Map<String, FlowNodeRequest> nodeByKey
    ) {
        if (!linkResult.canValidateConnections()) {
            return new LinkReferenceResult(List.of(), false, List.of());
        }
        List<FlowStructureValidationError> errors = new ArrayList<>();
        List<IndexedLink> validLinks = new ArrayList<>();
        boolean canValidateConnections = true;

        for (IndexedLink indexedLink : linkResult.indexedLinks()) {
            FlowLinkRequest link = indexedLink.link();
            String fieldPath = "links[" + indexedLink.requestIndex() + "]";
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
            if (!hasAllReferences) {
                canValidateConnections = false;
            } else {
                validLinks.add(indexedLink);
            }
        }
        return new LinkReferenceResult(
                List.copyOf(validLinks),
                canValidateConnections,
                List.copyOf(errors)
        );
    }

    //존재하는 노드끼리 연결된 링크의 중복 여부, 연결 방향과 포트 규칙을 검사한다.
    public LinkRulesResult validateBusinessRules(
            LinkReferenceResult linkRefResult,
            Map<String, FlowNodeRequest> nodeByKey
    ) {
        if (!linkRefResult.canValidateConnections()) {
            return new LinkRulesResult(Set.of(), false, List.of());
        }
        List<FlowStructureValidationError> errors = new ArrayList<>();
        Set<SourcePortKey> sourcePort = new HashSet<>();
        Set<String> sourceNodeKeys = new HashSet<>();
        boolean canValidateConnections = true;

        for (IndexedLink indexedLink : linkRefResult.validIndexedLinks()) {
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
            boolean validPorts = validatePorts(link, source, fieldPath, errors);
            boolean validDirection = validateDirection(link, source, target, fieldPath, errors);
            if (!validPorts || !validDirection) {
                canValidateConnections = false;
            }
        }
        return new LinkRulesResult(Set.copyOf(sourceNodeKeys), canValidateConnections, List.copyOf(errors));
    }

    //요청 순서를 유지한 채 IndexedLink에서 실제 링크만 추출합니다.
    public List<FlowLinkRequest> extractLinks(LinkReferenceResult linkRefResult) {
        List<FlowLinkRequest> links = new ArrayList<>();
        for (IndexedLink indexedLink : linkRefResult.validIndexedLinks()) {
            links.add(indexedLink.link());
        }
        return List.copyOf(links);
    }

    //액션 출력과 트리거 입력에 대한 규칙을 검증합니다.
    //DTO의 @NotNull과 별개로 직접 호출에서 누락된 nodeType을 안전하게 건너뜁니다.
    @SuppressWarnings({"java:S2583", "java:S2589"})
    private boolean validateDirection(
            FlowLinkRequest link,
            FlowNodeRequest source,
            FlowNodeRequest target,
            String fieldPath,
            List<FlowStructureValidationError> errors
    ) {
        boolean valid = true;
        if (source.nodeType() != null && source.nodeType().getCategory() == Category.ACTION) {
            addError(
                    errors,
                    FlowStructureErrorCode.ACTION_OUTPUT_LINK,
                    link.sourceClientNodeKey(),
                    fieldPath + ".sourceClientNodeKey",
                    "액션 노드는 종착점이므로 출력 링크를 가질 수 없습니다."
            );
            valid = false;
        }
        if (target.nodeType() != null && target.nodeType().getCategory() == Category.TRIGGER) {
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
    //DTO의 @NotNull과 별개로 직접 호출에서 누락된 nodeType을 안전하게 건너뜁니다.
    @SuppressWarnings({"java:S2583", "java:S2589"})
    private boolean validatePorts(
            FlowLinkRequest link,
            FlowNodeRequest source,
            String fieldPath,
            List<FlowStructureValidationError> errors
    ) {
        boolean valid = true;
        if (source.nodeType() != null
                && !source.nodeType().getPortSchema().outputPorts(null).contains(link.sourcePort())) {
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

    //Trigger는 출력 링크가 필요하고, Filter는 true 경로가 필요합니다.
    //Filter의 false 경로는 실행기에서 링크 없이 정상 종료할 수 있습니다.
    public List<FlowStructureValidationError> validateMissingOutputLinks(
            NodeValidationResult nodeResult,
            LinkReferenceResult linkRefResult,
            Set<String> sourceNodeKeys
    ) {
        if (!nodeResult.canValidateConnections() || !linkRefResult.canValidateConnections()) {
            return List.of();
        }
        List<FlowStructureValidationError> errors = new ArrayList<>();
        for (FlowNodeRequest node : nodeResult.nodesByKey().values()) {
            Category category = node.nodeType().getCategory();
            if (category == Category.ACTION) {
                continue;
            }
            boolean hasRequiredOutput = sourceNodeKeys.contains(node.clientNodeKey());
            if (category == Category.FILTER) {
                hasRequiredOutput = linkRefResult.validIndexedLinks().stream()
                        .anyMatch(indexedLink -> indexedLink.link().sourceClientNodeKey()
                                .equals(node.clientNodeKey())
                                && "true".equals(indexedLink.link().sourcePort()));
            }
            if (!hasRequiredOutput) {
                addError(
                        errors,
                        FlowStructureErrorCode.MISSING_OUTPUT_LINK,
                        node.clientNodeKey(),
                        "nodes",
                        category == Category.FILTER
                                ? "Filter는 true 출력 링크를 가져야 합니다."
                                : "Trigger는 출력 링크를 가져야 합니다."
                );
            }
        }
        return List.copyOf(errors);
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

    public record LinkReferenceResult(
            List<IndexedLink> validIndexedLinks,
            boolean canValidateConnections,
            List<FlowStructureValidationError> errors
    ) {
    }

    public record LinkRulesResult(
            // Set 사용 이유: Set.contains(key): O(1) > List.contains(key): O(n)
            // 중복 없는 source node의 존재 여부를 조회하면서 큰 성능 차이는 아니지만 Set이 더 조회하기 빠름
            Set<String> sourceNodeKeys,
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
