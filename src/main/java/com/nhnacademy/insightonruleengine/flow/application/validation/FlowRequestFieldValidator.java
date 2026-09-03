package com.nhnacademy.insightonruleengine.flow.application.validation;

import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowGraph;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowValidationErrorReason;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.LinkErrorCode;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.NodeErrorCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 저장 전 요청의 Node·Link 필수값을 검사하고, 그래프 검증이 쓸 {@link FlowGraph}로 옮긴다.
 *
 * <p>이 단계는 요청 경로에만 필요하다. 저장된 정의는 이미 필수값이 보장돼 있어 곧바로
 * {@link FlowGraphValidator}로 간다.
 *
 * <p>누락된 필드는 사용자가 고칠 위치를 알 수 있도록 요청 인덱스를 담은 필드 경로와 함께 하나씩 기록한다.
 * Bean Validation을 거치지 않고 직접 생성한 요청도 이 검증기를 통과할 수 있어 null 검사를 유지한다.
 */
@Component
public class FlowRequestFieldValidator {

    private static final String NODES_FIELD = "nodes";
    private static final String LINKS_FIELD = "links";

    public Result validate(List<FlowNodeRequest> nodes, List<FlowLinkRequest> links) {
        List<FlowStructureValidationError> errors = new ArrayList<>();
        NodeResult nodeResult = validateNodes(nodes, errors);
        LinkResult linkResult = validateLinks(links, errors);
        return new Result(
                new FlowGraph(nodeResult.nodes(), linkResult.links()),
                nodeResult.valid(),
                linkResult.valid(),
                List.copyOf(errors)
        );
    }

    private NodeResult validateNodes(
            List<FlowNodeRequest> nodes,
            List<FlowStructureValidationError> errors
    ) {
        if (nodes == null || nodes.isEmpty()) {
            addError(errors, NodeErrorCode.EMPTY_NODES, null, NODES_FIELD, "노드는 필수입니다.");
            return new NodeResult(List.of(), false);
        }
        List<FlowGraph.Node> graphNodes = new ArrayList<>();
        Set<String> nodeKeys = new HashSet<>();
        boolean valid = true;
        for (int index = 0; index < nodes.size(); index++) {
            if (!validateAndIndexNode(nodes.get(index), index, nodeKeys, graphNodes, errors)) {
                valid = false;
            }
        }
        return new NodeResult(List.copyOf(graphNodes), valid);
    }

    @SuppressWarnings({"java:S2583", "java:S2589"})
    private boolean validateAndIndexNode(
            FlowNodeRequest node,
            int requestIndex,
            Set<String> nodeKeys,
            List<FlowGraph.Node> graphNodes,
            List<FlowStructureValidationError> errors
    ) {
        String fieldPath = NODES_FIELD + "[" + requestIndex + "]";
        if (node == null) {
            addError(errors, NodeErrorCode.NULL_NODE, null, fieldPath, "노드는 null일 수 없습니다.");
            return false;
        }

        boolean hasClientNodeKey = node.clientNodeKey() != null && !node.clientNodeKey().isBlank();
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

        // key가 없으면 Link가 이 Node를 가리킬 수 없어 그래프에 넣지 않는다.
        if (!hasClientNodeKey) {
            return false;
        }
        boolean duplicateNodeKey = !nodeKeys.add(node.clientNodeKey());
        if (duplicateNodeKey) {
            addError(
                    errors,
                    NodeErrorCode.DUPLICATE_CLIENT_NODE_KEY,
                    node.clientNodeKey(),
                    fieldPath + ".clientNodeKey",
                    "clientKey는 중복될 수 없습니다."
            );
        } else {
            graphNodes.add(new FlowGraph.Node(node.clientNodeKey(), node.nodeType()));
        }
        return hasNodeType && !duplicateNodeKey;
    }

    private LinkResult validateLinks(
            List<FlowLinkRequest> links,
            List<FlowStructureValidationError> errors
    ) {
        if (links == null || links.isEmpty()) {
            addError(errors, LinkErrorCode.EMPTY_LINKS, null, LINKS_FIELD, "링크는 필수입니다.");
            return new LinkResult(List.of(), false);
        }
        List<FlowGraph.Link> graphLinks = new ArrayList<>();
        boolean valid = true;
        for (int index = 0; index < links.size(); index++) {
            FlowLinkRequest link = links.get(index);
            String fieldPath = FlowGraph.Link.fieldPathAt(index);
            if (link == null) {
                addError(errors, LinkErrorCode.NULL_LINK, null, fieldPath, "링크는 null일 수 없습니다.");
                valid = false;
            } else if (validateLinkRequiredFields(link, fieldPath, errors)) {
                graphLinks.add(FlowGraph.Link.at(
                        index,
                        link.sourceClientNodeKey(),
                        link.sourcePort(),
                        link.targetClientNodeKey(),
                        link.targetPort()
                ));
            } else {
                valid = false;
            }
        }
        return new LinkResult(List.copyOf(graphLinks), valid);
    }

    @SuppressWarnings({"java:S2583", "java:S2589"})
    private boolean validateLinkRequiredFields(
            FlowLinkRequest link,
            String fieldPath,
            List<FlowStructureValidationError> errors
    ) {
        boolean hasAllRequiredFields = true;
        if (link.sourceClientNodeKey() == null || link.sourceClientNodeKey().isBlank()) {
            addError(
                    errors,
                    LinkErrorCode.MISSING_SOURCE_CLIENT_NODE_KEY,
                    null,
                    fieldPath + ".sourceClientNodeKey",
                    "sourceClientNodeKey는 필수입니다."
            );
            hasAllRequiredFields = false;
        }
        if (link.targetClientNodeKey() == null || link.targetClientNodeKey().isBlank()) {
            addError(
                    errors,
                    LinkErrorCode.MISSING_TARGET_CLIENT_NODE_KEY,
                    null,
                    fieldPath + ".targetClientNodeKey",
                    "targetClientNodeKey는 필수입니다."
            );
            hasAllRequiredFields = false;
        }
        if (link.sourcePort() == null || link.sourcePort().isBlank()) {
            addError(
                    errors,
                    LinkErrorCode.MISSING_SOURCE_PORT,
                    link.sourceClientNodeKey(),
                    fieldPath + ".sourcePort",
                    "sourcePort는 필수입니다."
            );
            hasAllRequiredFields = false;
        }
        if (link.targetPort() == null || link.targetPort().isBlank()) {
            addError(
                    errors,
                    LinkErrorCode.MISSING_TARGET_PORT,
                    link.targetClientNodeKey(),
                    fieldPath + ".targetPort",
                    "targetPort는 필수입니다."
            );
            hasAllRequiredFields = false;
        }
        return hasAllRequiredFields;
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

    /**
     * @param nodeFieldsValid NodeType 기반 규칙을 판단할 수 있는지. 타입 누락이나 key 중복이 있으면 false다.
     * @param linkFieldsValid 연결을 판단할 수 있는지. 필수값이 빠진 Link가 하나라도 있으면 false다.
     */
    public record Result(
            FlowGraph graph,
            boolean nodeFieldsValid,
            boolean linkFieldsValid,
            List<FlowStructureValidationError> errors
    ) {
    }

    private record NodeResult(List<FlowGraph.Node> nodes, boolean valid) {
    }

    private record LinkResult(List<FlowGraph.Link> links, boolean valid) {
    }
}
