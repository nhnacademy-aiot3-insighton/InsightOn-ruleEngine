package com.nhnacademy.insightonruleengine.flow.definition;

import com.nhnacademy.insightonruleengine.flow.exception.DuplicateFlowDefinitionKeyException;
import com.nhnacademy.insightonruleengine.flow.exception.LinkNotFoundException;
import com.nhnacademy.insightonruleengine.flow.exception.NodeNotFoundException;
import java.util.HashMap;
import java.util.Map;

public class FlowDefinitionIndex {

    private final Map<Long, NodeDefinition> nodeDefinitionMap;
    private final Map<SourcePortKey, LinkDefinition> linkDefinitionMap;

    // FlowDefinition을 조회하기 쉽게 해주려는곳
    public FlowDefinitionIndex(FlowDefinition flowDefinition) {
        if (flowDefinition == null) {
            throw new IllegalArgumentException("flowDefinition는 null이면 안됩니다.");
        }
        this.nodeDefinitionMap = indexNodes(flowDefinition);
        this.linkDefinitionMap = indexLinks(flowDefinition);
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
    public LinkDefinition requireLink(
            Long sourceNodeId,
            String sourcePort
    ) {
        LinkDefinition link = linkDefinitionMap.get(
                new SourcePortKey(sourceNodeId, sourcePort)
        );

        if (link == null) {
            throw new LinkNotFoundException(sourceNodeId, sourcePort);
        }

        return link;
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
    private Map<SourcePortKey, LinkDefinition> indexLinks(FlowDefinition flowDefinition) {
        Map<SourcePortKey, LinkDefinition> result = new HashMap<>();
        for (LinkDefinition link : flowDefinition.links()) {
            SourcePortKey key = new SourcePortKey(link.sourceNodeId(), link.sourcePort());
            LinkDefinition duplicate = result.putIfAbsent(key, link);
            if (duplicate != null) {
                throw new DuplicateFlowDefinitionKeyException(link.sourceNodeId(), link.sourcePort());
            }
        }
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

}
