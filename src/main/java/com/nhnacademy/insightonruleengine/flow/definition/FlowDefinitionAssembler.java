package com.nhnacademy.insightonruleengine.flow.definition;

import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.exception.FlowNotActiveException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowNotFoundException;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.domain.Link;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.repository.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.repository.NodeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlowDefinitionAssembler {

    private final FlowRepository flowRepository;
    private final NodeRepository nodeRepository;
    private final LinkRepository linkRepository;

    // FlowDefinition에 NodeDefinition과 LinkDefinition을 조합해주는곳
    public FlowDefinition assemble(Long groupId, Long flowId) {
        Flow flow = flowRepository.findById(flowId)
                .filter(foundFlow -> foundFlow.getGroupId().equals(groupId))
                .orElseThrow(() -> new FlowNotFoundException(groupId, flowId));
        List<Node> nodes = nodeRepository.findByFlowId(flowId);
        List<Link> links = linkRepository.findByFlowId(flowId);
        return new FlowDefinition(
                flow.getId(),
                flow.getGroupId(),
                flow.getLocationId(),
                flow.getName(),
                flow.getDescription(),
                flow.getStatus(),
                flow.getCreatedDate(),
                nodes.stream().map(this::assembleNode).toList(),
                links.stream().map(this::assembleLink).toList()
        );
    }

    // 활성된 상태의 플로우만 실행
    public FlowDefinition assembleActive(Long groupId, Long flowId) {
        FlowDefinition definition = assemble(groupId, flowId);
        if (definition.status() != FlowStatus.ACTIVE) {
            throw new FlowNotActiveException(flowId, definition.status());
        }
        return definition;
    }

    private NodeDefinition assembleNode(Node node) {
        return new NodeDefinition(
                node.getId(),
                node.getNodeType(),
                node.getConfiguration()
        );
    }

    private LinkDefinition assembleLink(Link link) {
        return new LinkDefinition(
                link.getId(),
                link.getFlowId(),
                link.getSourceNodeId(),
                link.getTargetNodeId(),
                link.getSourcePort(),
                link.getTargetPort()
        );
    }
}
