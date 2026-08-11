package com.nhnacademy.insightonruleengine.flow.validation;

import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.dto.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowNodeRequest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

//순환 참조에 관한 검증
@Component
public class FlowPathValidator {

    //기본 구조 검증을 통과한 Node와 Link에서 도달성, Action 경로와 Cycle 오류를 찾습니다.
    public List<FlowStructureValidationError> validate(
            Map<String, FlowNodeRequest> nodesByKey,
            List<FlowLinkRequest> links
    ) {
        TraversalIndex traversalIndex = buildTraversalIndex(nodesByKey.keySet(), links);
        String triggerKey = findTriggerKey(nodesByKey);
        Set<String> reachableNodeKeys = traverse(Set.of(triggerKey), traversalIndex.outgoing());
        Set<String> actionKeys = findActionKeys(nodesByKey);
        Set<String> canReachActionNodeKeys = traverse(actionKeys, traversalIndex.incoming());

        List<FlowStructureValidationError> errors = new ArrayList<>();
        validateReachability(nodesByKey, reachableNodeKeys, errors);
        validateActionPaths(nodesByKey, canReachActionNodeKeys, errors);
        validateCycle(nodesByKey.keySet(), traversalIndex.outgoing(), errors);
        return List.copyOf(errors);
    }

    //각 Source와 Target의 정방향·역방향 연결을 한 번에 색인한다.
    private TraversalIndex buildTraversalIndex(
            Set<String> nodeKeys,
            List<FlowLinkRequest> links
    ) {
        Map<String, List<String>> outgoing = emptyAdjacency(nodeKeys);
        Map<String, List<String>> incoming = emptyAdjacency(nodeKeys);
        for (FlowLinkRequest link : links) {
            outgoing.get(link.sourceClientNodeKey()).add(link.targetClientNodeKey());
            incoming.get(link.targetClientNodeKey()).add(link.sourceClientNodeKey());
        }
        return new TraversalIndex(
                immutableAdjacency(outgoing),
                immutableAdjacency(incoming)
        );
    }

    //Link가 없는 Node도 탐색 Map에 남도록 모든 Node Key를 먼저 등록한다.
    private Map<String, List<String>> emptyAdjacency(Set<String> nodeKeys) {
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        nodeKeys.forEach(key -> adjacency.put(key, new ArrayList<>()));
        return adjacency;
    }

    //인접 목록의 내부 List까지 복사해 탐색 도중 변경되지 않게 한다.
    private Map<String, List<String>> immutableAdjacency(
            Map<String, List<String>> adjacency
    ) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        adjacency.forEach((key, targets) -> copy.put(key, List.copyOf(targets)));
        return Collections.unmodifiableMap(copy);
    }

    //검증된 역할 목록에서 하나뿐인 Trigger Key를 찾는다.
    private String findTriggerKey(Map<String, FlowNodeRequest> nodesByKey) {
        for (FlowNodeRequest node : nodesByKey.values()) {
            if (node.nodeType().getCategory() == NodeType.Category.TRIGGER) {
                return node.clientNodeKey();
            }
        }
        throw new IllegalArgumentException("경로 검증에는 Trigger Node가 필요합니다.");
    }

    //역방향 탐색을 시작할 Action Key를 중복 없이 모은다.
    private Set<String> findActionKeys(Map<String, FlowNodeRequest> nodesByKey) {
        Set<String> actionKeys = new LinkedHashSet<>();
        for (FlowNodeRequest node : nodesByKey.values()) {
            if (node.nodeType().getCategory() == NodeType.Category.ACTION) {
                actionKeys.add(node.clientNodeKey());
            }
        }
        return Set.copyOf(actionKeys);
    }

    //Trigger에서 방문하지 못한 Node마다 수정 가능한 오류를 기록한다.
    private void validateReachability(
            Map<String, FlowNodeRequest> nodesByKey,
            Set<String> reachableNodeKeys,
            List<FlowStructureValidationError> errors
    ) {
        for (FlowNodeRequest node : nodesByKey.values()) {
            if (!reachableNodeKeys.contains(node.clientNodeKey())) {
                addError(errors, FlowStructureErrorCode.UNREACHABLE_NODE,
                        node.clientNodeKey(), "nodes",
                        "Trigger에서 도달할 수 없는 Node가 있습니다.");
            }
        }
    }

    //어떤 Action으로도 이어지지 않는 비-Action Node를 역방향 탐색 결과로 찾는다.
    private void validateActionPaths(
            Map<String, FlowNodeRequest> nodesByKey,
            Set<String> canReachActionNodeKeys,
            List<FlowStructureValidationError> errors
    ) {
        for (FlowNodeRequest node : nodesByKey.values()) {
            if (node.nodeType().getCategory() != NodeType.Category.ACTION
                    && !canReachActionNodeKeys.contains(node.clientNodeKey())) {
                addError(errors, FlowStructureErrorCode.CANNOT_REACH_ACTION,
                        node.clientNodeKey(), "nodes",
                        "Node에서 도달할 수 있는 Action이 없습니다.");
            }
        }
    }

    //Cycle 존재 여부를 연결 구조 오류로 변환해 다른 경로 오류와 함께 반환한다.
    private void validateCycle(
            Set<String> nodeKeys,
            Map<String, List<String>> outgoing,
            List<FlowStructureValidationError> errors
    ) {
        if (hasCycle(nodeKeys, outgoing)) {
            addError(errors, FlowStructureErrorCode.CYCLE_DETECTED, null, "links",
                    "Flow의 Node와 Link 연결에 Cycle이 있습니다.");
        }
    }

    //여러 시작점에서 주어진 Link 방향으로 방문할 수 있는 Node를 한 번씩 찾는다.
    private Set<String> traverse(
            Set<String> startKeys,
            Map<String, List<String>> adjacency
    ) {
        Set<String> visited = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(startKeys);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (visited.add(current)) {
                pending.addAll(adjacency.getOrDefault(current, List.of()));
            }
        }
        return Set.copyOf(visited);
    }

    //DFS 방문 상태(방문 중, 방문 완료)를 탐색해 Cycle을 찾는다.
    private boolean hasCycle(
            Set<String> nodeKeys,
            Map<String, List<String>> outgoing
    ) {
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();

        for (String nodeKey : nodeKeys) {
            if (!visited.contains(nodeKey)) {
                if (dfsHasCycle(nodeKey, outgoing, visiting, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean dfsHasCycle(
            String current,
            Map<String, List<String>> outgoing,
            Set<String> visiting,
            Set<String> visited
    ) {
        visiting.add(current);
        List<String> targets = outgoing.getOrDefault(current, List.of());
        for (String target : targets) {
            if (visiting.contains(target)) {
                return true;
            }
            if (!visited.contains(target)) {
                if (dfsHasCycle(target, outgoing, visiting, visited)) {
                    return true;
                }
            }
        }
        visiting.remove(current);
        visited.add(current);
        return false;
    }

    //경로 알고리즘도 기본 검증과 같은 오류 필드를 채우도록 생성 규칙을 맞춘다.
    private void addError(
            List<FlowStructureValidationError> errors,
            FlowValidationErrorReason code,
            String clientNodeKey,
            String fieldPath,
            String message
    ) {
        errors.add(new FlowStructureValidationError(code, clientNodeKey, fieldPath, message));
    }

    private record TraversalIndex(
            Map<String, List<String>> outgoing,
            Map<String, List<String>> incoming
    ) {
    }
}
