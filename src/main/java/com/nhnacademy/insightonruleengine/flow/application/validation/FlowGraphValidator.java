package com.nhnacademy.insightonruleengine.flow.application.validation;

import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowGraph;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureErrorCode;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowValidationErrorReason;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType.Category;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Node와 Link로 이루어진 그래프 규칙을 검증한다.
 *
 * <p>저장 전 요청과 저장된 정의가 모두 {@link FlowGraph}로 들어오므로, 두 경로가 같은 규칙과 같은 오류
 * 코드를 공유한다.
 *
 * <p>검증은 단계로 나뉘며 앞 단계가 실패하면 뒤 단계는 건너뛴다. 예를 들어 Link가 존재하지 않는 Node를
 * 가리키면 그 Link로 방향·포트·경로를 판단할 수 없으므로, 사용자가 먼저 고쳐야 할 참조 오류만 알려주고
 * 뒤 단계에서 파생되는 오류는 만들지 않는다.
 */
@Component
public class FlowGraphValidator {

    private static final String NODES_FIELD = "nodes";
    private static final String LINKS_FIELD = "links";
    private static final String SOURCE_NODE_KEY_FIELD = ".sourceClientNodeKey";
    private static final String TARGET_NODE_KEY_FIELD = ".targetClientNodeKey";
    private static final String ALLOWED_TARGET_PORT = "in";

    /** 필드 검증이 필요 없는 입력(저장된 정의)을 검증한다. */
    public List<FlowStructureValidationError> validate(FlowGraph graph) {
        return validate(graph, true, true);
    }

    /**
     * @param nodeFieldsValid Node 필수값이 모두 채워져 NodeType 기반 규칙을 판단할 수 있는지
     * @param linkFieldsValid Link 필수값이 모두 채워져 연결을 판단할 수 있는지
     */
    public List<FlowStructureValidationError> validate(
            FlowGraph graph, boolean nodeFieldsValid, boolean linkFieldsValid
    ) {
        if (graph == null) {
            throw new IllegalArgumentException("graph는 필수입니다.");
        }
        Map<String, FlowGraph.Node> nodesByKey = graph.nodesByKey();
        List<FlowStructureValidationError> errors = new ArrayList<>();

        List<FlowGraph.Link> resolvedLinks = List.of();
        boolean referencesValid = false;
        if (linkFieldsValid) {
            ReferenceResult references = validateReferences(graph.links(), nodesByKey, errors);
            resolvedLinks = references.resolvedLinks();
            referencesValid = references.valid();
        }

        boolean rolesValid = nodeFieldsValid && validateRoles(nodesByKey.values(), errors);

        boolean linkRulesValid = nodeFieldsValid && referencesValid
                && validateLinkRules(resolvedLinks, nodesByKey, errors);

        if (nodeFieldsValid && referencesValid) {
            validateMissingOutputLinks(nodesByKey.values(), resolvedLinks, errors);
        }

        if (nodeFieldsValid && linkFieldsValid && referencesValid && linkRulesValid && rolesValid) {
            validatePaths(nodesByKey, resolvedLinks, errors);
        }

        return List.copyOf(errors);
    }

    // Link가 가리키는 Node가 실제로 있는지 확인하고, 양쪽이 모두 존재하는 Link만 다음 단계로 넘긴다.
    private ReferenceResult validateReferences(
            List<FlowGraph.Link> links,
            Map<String, FlowGraph.Node> nodesByKey,
            List<FlowStructureValidationError> errors
    ) {
        List<FlowGraph.Link> resolvedLinks = new ArrayList<>();
        boolean valid = true;
        for (FlowGraph.Link link : links) {
            boolean hasAllReferences = true;
            if (!nodesByKey.containsKey(link.sourceKey())) {
                addError(
                        errors,
                        FlowStructureErrorCode.MISSING_SOURCE_NODE,
                        link.sourceKey(),
                        link.fieldPath() + SOURCE_NODE_KEY_FIELD,
                        "링크에 지정된 소스 노드 ID와 일치하는 노드가 없습니다."
                );
                hasAllReferences = false;
            }
            if (!nodesByKey.containsKey(link.targetKey())) {
                addError(
                        errors,
                        FlowStructureErrorCode.MISSING_TARGET_NODE,
                        link.targetKey(),
                        link.fieldPath() + TARGET_NODE_KEY_FIELD,
                        "링크에 지정된 타겟 노드 ID와 일치하는 노드가 없습니다."
                );
                hasAllReferences = false;
            }
            if (hasAllReferences) {
                resolvedLinks.add(link);
            } else {
                valid = false;
            }
        }
        return new ReferenceResult(List.copyOf(resolvedLinks), valid);
    }

    // 하나의 시작점과 하나 이상의 종착점이 있는지 확인한다.
    private boolean validateRoles(
            Collection<FlowGraph.Node> nodes,
            List<FlowStructureValidationError> errors
    ) {
        boolean valid = true;
        if (countCategory(nodes, Category.TRIGGER) != 1L) {
            addError(
                    errors,
                    FlowStructureErrorCode.INVALID_TRIGGER_COUNT,
                    null,
                    NODES_FIELD,
                    "Trigger Node는 정확히 하나여야 합니다."
            );
            valid = false;
        }
        if (countCategory(nodes, Category.ACTION) == 0L) {
            addError(
                    errors,
                    FlowStructureErrorCode.MISSING_ACTION,
                    null,
                    NODES_FIELD,
                    "Action Node는 하나 이상이어야 합니다."
            );
            valid = false;
        }
        return valid;
    }

    // nodeType이 없는 Node는 역할을 판단할 수 없으므로 세지 않는다.
    private long countCategory(Collection<FlowGraph.Node> nodes, Category category) {
        return nodes.stream()
                .filter(node -> node.nodeType() != null && node.nodeType().getCategory() == category)
                .count();
    }

    // 중복 여부, 연결 방향, 포트 계약, fan-out 대상을 함께 검사한다.
    private boolean validateLinkRules(
            List<FlowGraph.Link> links,
            Map<String, FlowGraph.Node> nodesByKey,
            List<FlowStructureValidationError> errors
    ) {
        Map<SourcePortKey, List<FlowGraph.Link>> linksBySourcePort = new LinkedHashMap<>();
        Set<LinkKey> linkKeys = new HashSet<>();
        boolean valid = true;

        for (FlowGraph.Link link : links) {
            if (link.sourceKey().equals(link.targetKey())) {
                addError(
                        errors,
                        FlowStructureErrorCode.SELF_LOOP,
                        link.sourceKey(),
                        link.fieldPath(),
                        "노드는 자기 자신과 연결 할 수 없습니다."
                );
                valid = false;
            }
            LinkKey linkKey = new LinkKey(
                    link.sourceKey(),
                    link.sourcePort(),
                    link.targetKey(),
                    link.targetPort()
            );
            if (!linkKeys.add(linkKey)) {
                addError(
                        errors,
                        FlowStructureErrorCode.DUPLICATE_LINK,
                        link.sourceKey(),
                        link.fieldPath(),
                        "출발·도착 노드와 포트가 모두 같은 링크를 중복 사용할 수 없습니다."
                );
                valid = false;
            }
            linksBySourcePort
                    .computeIfAbsent(
                            new SourcePortKey(link.sourceKey(), link.sourcePort()),
                            ignored -> new ArrayList<>())
                    .add(link);

            FlowGraph.Node source = nodesByKey.get(link.sourceKey());
            FlowGraph.Node target = nodesByKey.get(link.targetKey());
            boolean validPorts = validatePorts(link, source, errors);
            boolean validDirection = validateDirection(link, source, target, errors);
            if (!validPorts || !validDirection) {
                valid = false;
            }
        }
        if (!validateFanOutTargets(linksBySourcePort, nodesByKey, errors)) {
            valid = false;
        }
        return valid;
    }

    // 소스 포트는 NodeType의 출력 포트여야 하고, 타겟 포트는 in만 허용한다.
    private boolean validatePorts(
            FlowGraph.Link link, FlowGraph.Node source, List<FlowStructureValidationError> errors
    ) {
        boolean valid = true;
        if (!source.nodeType().getPortSchema().outputPorts(null).contains(link.sourcePort())) {
            addError(
                    errors,
                    FlowStructureErrorCode.INVALID_PORT,
                    link.sourceKey(),
                    link.fieldPath() + ".sourcePort",
                    "소스포트와 노드타입이 일치하지 않습니다."
            );
            valid = false;
        }
        if (!ALLOWED_TARGET_PORT.equals(link.targetPort())) {
            addError(
                    errors,
                    FlowStructureErrorCode.INVALID_PORT,
                    link.targetKey(),
                    link.fieldPath() + ".targetPort",
                    "타겟 포트는 in만 사용할 수 있습니다."
            );
            valid = false;
        }
        return valid;
    }

    // Action은 종착점, Trigger는 시작점이며 Schedule은 장치 제어에 직접 연결해야 한다.
    private boolean validateDirection(
            FlowGraph.Link link, FlowGraph.Node source, FlowGraph.Node target,
            List<FlowStructureValidationError> errors
    ) {
        boolean valid = true;
        if (source.nodeType().getCategory() == Category.ACTION) {
            addError(
                    errors,
                    FlowStructureErrorCode.ACTION_OUTPUT_LINK,
                    link.sourceKey(),
                    link.fieldPath() + SOURCE_NODE_KEY_FIELD,
                    "액션 노드는 종착점이므로 출력 링크를 가질 수 없습니다."
            );
            valid = false;
        }
        if (target.nodeType().getCategory() == Category.TRIGGER) {
            addError(
                    errors,
                    FlowStructureErrorCode.TRIGGER_INPUT_LINK,
                    link.targetKey(),
                    link.fieldPath() + TARGET_NODE_KEY_FIELD,
                    "트리거 노드는 시작점이므로 입력 링크를 가질 수 없습니다."
            );
            valid = false;
        }
        if (source.nodeType() == NodeType.SCHEDULE
                && target.nodeType() != NodeType.ACTUATOR_CONTROL) {
            addError(
                    errors,
                    FlowStructureErrorCode.INVALID_SCHEDULE_TARGET,
                    link.sourceKey(),
                    link.fieldPath() + TARGET_NODE_KEY_FIELD,
                    "Schedule 노드는 Actuator Control 노드에 직접 연결해야 합니다."
            );
            valid = false;
        }
        return valid;
    }

    // 같은 출력 포트에서 갈라지는 링크는 대상이 모두 Action일 때만 허용한다.
    private boolean validateFanOutTargets(
            Map<SourcePortKey, List<FlowGraph.Link>> linksBySourcePort,
            Map<String, FlowGraph.Node> nodesByKey,
            List<FlowStructureValidationError> errors
    ) {
        boolean valid = true;
        for (Map.Entry<SourcePortKey, List<FlowGraph.Link>> entry : linksBySourcePort.entrySet()) {
            List<FlowGraph.Link> links = entry.getValue();
            if (links.size() <= 1) {
                continue;
            }
            FlowGraph.Link invalidTarget = findInvalidFanOutTarget(links, nodesByKey);
            if (invalidTarget != null) {
                addError(
                        errors,
                        FlowStructureErrorCode.INVALID_FAN_OUT_TARGET,
                        entry.getKey().sourceKey(),
                        invalidTarget.fieldPath() + TARGET_NODE_KEY_FIELD,
                        "동일 출력 포트의 복수 링크는 모든 대상이 Action Node일 때만 허용됩니다."
                );
                valid = false;
            }
        }
        return valid;
    }

    // 같은 출력 포트를 공유하는 링크들 중 Action이 아닌 곳으로 향하는 첫 링크를 찾는다.
    private FlowGraph.Link findInvalidFanOutTarget(
            List<FlowGraph.Link> links, Map<String, FlowGraph.Node> nodesByKey
    ) {
        return links.stream()
                .filter(link -> nodesByKey.get(link.targetKey()).nodeType().getCategory()
                        != Category.ACTION)
                .findFirst()
                .orElse(null);
    }

    // Trigger는 출력 링크가 필요하고, Filter는 true 경로가 필요하다.
    // Filter의 false 경로는 실행기가 링크 없이 정상 종료할 수 있으므로 요구하지 않는다.
    private void validateMissingOutputLinks(
            Collection<FlowGraph.Node> nodes, List<FlowGraph.Link> links, List<FlowStructureValidationError> errors
    ) {
        Set<String> sourceNodeKeys = links.stream()
                .map(FlowGraph.Link::sourceKey)
                .collect(Collectors.toUnmodifiableSet());
        for (FlowGraph.Node node : nodes) {
            Category category = node.nodeType().getCategory();
            if (category == Category.ACTION) {
                continue;
            }
            boolean hasRequiredOutput = category == Category.FILTER
                    ? hasTrueOutputLink(links, node.key())
                    : sourceNodeKeys.contains(node.key());
            if (!hasRequiredOutput) {
                addError(
                        errors,
                        FlowStructureErrorCode.MISSING_OUTPUT_LINK,
                        node.key(),
                        NODES_FIELD,
                        category == Category.FILTER
                                ? "Filter는 true 출력 링크를 가져야 합니다."
                                : "Trigger는 출력 링크를 가져야 합니다."
                );
            }
        }
    }

    private boolean hasTrueOutputLink(List<FlowGraph.Link> links, String nodeKey) {
        return links.stream()
                .anyMatch(link -> link.sourceKey().equals(nodeKey)
                        && "true".equals(link.sourcePort()));
    }

    // 도달성, Action 경로, Cycle을 한 번의 색인으로 함께 확인한다.
    private void validatePaths(
            Map<String, FlowGraph.Node> nodesByKey,
            List<FlowGraph.Link> links,
            List<FlowStructureValidationError> errors
    ) {
        TraversalIndex traversalIndex = buildTraversalIndex(nodesByKey.keySet(), links);
        Set<String> reachableNodeKeys = traverse(
                Set.of(findTriggerKey(nodesByKey.values())),
                traversalIndex.outgoing()
        );
        Set<String> canReachActionNodeKeys = traverse(
                findActionKeys(nodesByKey.values()),
                traversalIndex.incoming()
        );

        for (FlowGraph.Node node : nodesByKey.values()) {
            if (!reachableNodeKeys.contains(node.key())) {
                addError(
                        errors,
                        FlowStructureErrorCode.UNREACHABLE_NODE,
                        node.key(),
                        NODES_FIELD,
                        "Trigger에서 도달할 수 없는 Node가 있습니다."
                );
            }
        }
        for (FlowGraph.Node node : nodesByKey.values()) {
            if (node.nodeType().getCategory() != Category.ACTION
                    && !canReachActionNodeKeys.contains(node.key())) {
                addError(
                        errors,
                        FlowStructureErrorCode.CANNOT_REACH_ACTION,
                        node.key(),
                        NODES_FIELD,
                        "Node에서 도달할 수 있는 Action이 없습니다."
                );
            }
        }
        if (hasCycle(nodesByKey.keySet(), traversalIndex.outgoing())) {
            addError(
                    errors,
                    FlowStructureErrorCode.CYCLE_DETECTED,
                    null,
                    LINKS_FIELD,
                    "Flow의 Node와 Link 연결에 Cycle이 있습니다."
            );
        }
    }

    // 정방향·역방향 인접 목록을 한 번에 만든다. Link가 없는 Node도 탐색 대상으로 남긴다.
    private TraversalIndex buildTraversalIndex(
            Set<String> nodeKeys, List<FlowGraph.Link> links
    ) {
        Map<String, List<String>> outgoing = emptyAdjacency(nodeKeys);
        Map<String, List<String>> incoming = emptyAdjacency(nodeKeys);
        for (FlowGraph.Link link : links) {
            outgoing.get(link.sourceKey()).add(link.targetKey());
            incoming.get(link.targetKey()).add(link.sourceKey());
        }
        return new TraversalIndex(immutableAdjacency(outgoing), immutableAdjacency(incoming));
    }

    private Map<String, List<String>> emptyAdjacency(Set<String> nodeKeys) {
        Map<String, List<String>> adjacency = new HashMap<>();
        nodeKeys.forEach(key -> adjacency.put(key, new ArrayList<>()));
        return adjacency;
    }

    private Map<String, List<String>> immutableAdjacency(Map<String, List<String>> adjacency) {
        Map<String, List<String>> copy = new HashMap<>();
        adjacency.forEach((key, targets) -> copy.put(key, List.copyOf(targets)));
        return Collections.unmodifiableMap(copy);
    }

    private String findTriggerKey(Collection<FlowGraph.Node> nodes) {
        return nodes.stream()
                .filter(node -> node.nodeType().getCategory() == Category.TRIGGER)
                .map(FlowGraph.Node::key)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("경로 검증에는 Trigger Node가 필요합니다."));
    }

    private Set<String> findActionKeys(Collection<FlowGraph.Node> nodes) {
        return nodes.stream()
                .filter(node -> node.nodeType().getCategory() == Category.ACTION)
                .map(FlowGraph.Node::key)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> traverse(Set<String> startKeys, Map<String, List<String>> adjacency) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(startKeys);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (visited.add(current)) {
                pending.addAll(adjacency.getOrDefault(current, List.of()));
            }
        }
        return Set.copyOf(visited);
    }

    private boolean hasCycle(Set<String> nodeKeys, Map<String, List<String>> outgoing) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String nodeKey : nodeKeys) {
            if (!visited.contains(nodeKey) && dfsHasCycle(nodeKey, outgoing, visiting, visited)) {
                return true;
            }
        }
        return false;
    }

    private boolean dfsHasCycle(
            String current, Map<String, List<String>> outgoing, Set<String> visiting, Set<String> visited
    ) {
        visiting.add(current);
        for (String target : outgoing.getOrDefault(current, List.of())) {
            if (visiting.contains(target)) {
                return true;
            }
            if (!visited.contains(target) && dfsHasCycle(target, outgoing, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(current);
        visited.add(current);
        return false;
    }

    private void addError(
            List<FlowStructureValidationError> errors,
            FlowValidationErrorReason code, String nodeKey, String fieldPath, String message
    ) {
        errors.add(new FlowStructureValidationError(code, nodeKey, fieldPath, message));
    }

    private record ReferenceResult(List<FlowGraph.Link> resolvedLinks, boolean valid) {
    }

    // 하나의 Node가 여러 출력 포트를 가질 수 있으므로 fan-out 판단에는 Node와 Port를 함께 쓴다.
    private record SourcePortKey(String sourceKey, String sourcePort) {
    }

    private record LinkKey(
            String sourceKey,
            String sourcePort,
            String targetKey,
            String targetPort
    ) {
    }

    private record TraversalIndex(
            Map<String, List<String>> outgoing,
            Map<String, List<String>> incoming
    ) {
    }
}
