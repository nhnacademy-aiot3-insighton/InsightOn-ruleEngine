package com.nhnacademy.insightonruleengine.flow.domain.definition;

import com.nhnacademy.insightonruleengine.flow.domain.exception.DuplicateFlowDefinitionKeyException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.LinkNotFoundException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.NodeNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FlowDefinitionIndex {

    private final Map<Long, NodeDefinition> nodeDefinitionMap;
    private final Map<SourcePortKey, List<LinkDefinition>> linkDefinitionsBySourcePort;

    // FlowDefinition을 조회하기 쉽게 해주려는곳
    public FlowDefinitionIndex(FlowDefinition flowDefinition) {
        if (flowDefinition == null) {
            throw new IllegalArgumentException("flowDefinition는 null이면 안됩니다.");
        }
        this.nodeDefinitionMap = indexNodes(flowDefinition);
        this.linkDefinitionsBySourcePort = indexLinks(flowDefinition);
    }

    // 노드 검증
    public NodeDefinition requireNode(
            Long nodeId
    ) {
        if (nodeId == null) {
            throw new IllegalArgumentException("nodeId는 필수입니다.");
        }
        NodeDefinition node = nodeDefinitionMap.get(nodeId);
        if (node == null) {
            throw new NodeNotFoundException(nodeId);
        }
        return node;
    }

    // 링크 검증
    public List<LinkDefinition> requireLinks(
            Long sourceNodeId,
            String sourcePort
    ) {
        List<LinkDefinition> links = linkDefinitionsBySourcePort.get(
                new SourcePortKey(sourceNodeId, sourcePort)
        );

        if (links == null) {
            throw new LinkNotFoundException(sourceNodeId, sourcePort);
        }

        return links;
    }

    //Source Node와 Port에 해당하는 다음 Link들을 요청 순서대로 조회합니다. FlowRunner에서 사용
    public List<LinkDefinition> findLinks(Long sourceNodeId, String sourcePort) {
        if (sourceNodeId == null || sourcePort == null) {
            return List.of();
        }
        return linkDefinitionsBySourcePort.getOrDefault(
                new SourcePortKey(sourceNodeId, sourcePort),
                List.of()
        );
    }

    // 인덱스 노드: 노드 조회를 위함 DB 저장이 끝난 FlowDefinition을 실행할 때 사용하는 메서드
    private Map<Long, NodeDefinition> indexNodes(FlowDefinition flowDefinition) {
        Map<Long, NodeDefinition> result = new HashMap<>();
        for (NodeDefinition node : flowDefinition.nodes()) {
            NodeDefinition duplicate = result.putIfAbsent(node.nodeId(), node);
            if (duplicate != null) {
                throw new DuplicateFlowDefinitionKeyException(node.nodeId());
            }
        }
        return Map.copyOf(result);
    }

    // 링크 노드: 링크 조회를 위함
    private Map<SourcePortKey, List<LinkDefinition>> indexLinks(FlowDefinition flowDefinition) {
        Map<SourcePortKey, List<LinkDefinition>> groupedLinks = new HashMap<>();
        Set<LinkKey> linkKeys = new HashSet<>();
        for (LinkDefinition link : flowDefinition.links()) {
            SourcePortKey key = new SourcePortKey(link.sourceNodeId(), link.sourcePort());
            LinkKey linkKey = new LinkKey(
                    link.sourceNodeId(),
                    link.sourcePort(),
                    link.targetNodeId(),
                    link.targetPort()
            );
            if (!linkKeys.add(linkKey)) {
                throw new DuplicateFlowDefinitionKeyException(
                        link.sourceNodeId(),
                        link.sourcePort(),
                        link.targetNodeId(),
                        link.targetPort()
                );
            }
            groupedLinks.computeIfAbsent(key, ignored -> new ArrayList<>()).add(link);
        }
        Map<SourcePortKey, List<LinkDefinition>> result = new HashMap<>();
        groupedLinks.forEach((key, links) -> result.put(key, List.copyOf(links)));
        return Map.copyOf(result);
    }

    // 하나의 노드가 여러 출력 포트를 가질 수 있으므로,
    // 노드 실행 결과에 해당하는 다음 링크를 찾기 위해 sourceNodeId와 sourcePort를 복합 키로 사용한다.
    private record SourcePortKey(Long sourceNodeId, String sourcePort) {
        private SourcePortKey {
            if (sourceNodeId == null) {
                throw new IllegalArgumentException("sourceNodeId는 null이면 안됩니다.");
            }
            if (sourcePort == null) {
                throw new IllegalArgumentException("sourcePort는 null이면 안됩니다.");
            }
        }
    }

    private record LinkKey(
            Long sourceNodeId,
            String sourcePort,
            Long targetNodeId,
            String targetPort
    ) {
    }

}
