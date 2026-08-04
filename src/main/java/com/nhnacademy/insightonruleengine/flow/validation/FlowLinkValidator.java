package com.nhnacademy.insightonruleengine.flow.validation;

import com.nhnacademy.insightonruleengine.flow.dto.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.validation.FlowNodeValidator.NodeValidationResult;
import com.nhnacademy.insightonruleengine.node.domain.NodeType.Category;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

//link를 검증, 노드와 연결 되는 부분을 검증
@Component
public class FlowLinkValidator {

    //소스포트와 타겟포트의 계약을 검증합니다 소스포트는 타입 검증, 타겟포트는 in 이어야만 허용.
    private boolean validatePort(
            FlowLinkRequest link,
            FlowNodeRequest node,
            String fieldPath,
            List<FlowStructureValidationError> errors
    ) {
        boolean valid = node.nodeType() != null;
        if (valid
                && !node.nodeType().getPortSchema().outputPorts(null).contains(link.sourcePort())) {

        }
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
