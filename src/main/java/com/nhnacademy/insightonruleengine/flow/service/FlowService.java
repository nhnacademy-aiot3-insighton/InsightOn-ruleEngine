package com.nhnacademy.insightonruleengine.flow.service;

import com.nhnacademy.insightonruleengine.flow.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.authorization.GroupRole;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.dto.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.dto.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowUpdateRequest;
import com.nhnacademy.insightonruleengine.flow.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowDeletionNotAllowedException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowNotFoundException;
import com.nhnacademy.insightonruleengine.flow.exception.InvalidFlowStatusTransitionException;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.domain.Link;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.repository.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.repository.NodeRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlowService {

    private static final String TARGET_PORT = "in";

    private final FlowRepository flowRepository;
    private final GroupAuthorizationService groupAuthorizationService;
    private final NodeRepository nodeRepository;
    private final LinkRepository linkRepository;

    // 새 Flow는 바로 실행되지 않도록 INACTIVE 상태로 저장합니다.
    @Transactional
    public FlowResponse create(Long groupId, Long userId, FlowCreateRequest request) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MANAGER);
        validateRequest(request);
        Flow flow = new Flow(
                groupId,
                request.locationId(),
                request.name(),
                request.description(),
                FlowStatus.INACTIVE);
        validate(flow);
        return toResponse(flowRepository.save(flow));
    }

    // 일반 목록에서는 휴지통의 Flow를 제외합니다.
    public List<FlowResponse> findAll(Long groupId, Long userId) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MEMBER);
        return flowRepository.findAllByGroupIdAndStatusNot(groupId, FlowStatus.ARCHIVED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 선택한 상태의 Flow만 조회합니다.
    public List<FlowResponse> findAll(Long groupId, Long userId, FlowStatus status) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MEMBER);
        return flowRepository.findAllByGroupIdAndStatus(groupId, status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 선택한 그룹, 장소, 상태에 맞는 Flow만 조회합니다.
    public List<FlowResponse> findAll(Long groupId, Long userId, Long locationId, FlowStatus status) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MEMBER);
        return flowRepository.findAllByGroupIdAndLocationIdAndStatus(groupId, locationId, status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 요청한 그룹에 속한 Flow의 상세 정보를 반환합니다.
    public FlowResponse findById(Long groupId, Long userId, Long flowId) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MEMBER);
        return toResponse(oneFlow(groupId, flowId));
    }

    // Flow가 없거나 다른 그룹의 Flow이면 같은 예외를 발생시킵니다.
    private Flow oneFlow(Long groupId, Long flowId) {
        return flowRepository.findById(flowId)
                .filter(flow -> flow.getGroupId().equals(groupId))
                .orElseThrow(() -> new FlowNotFoundException(groupId, flowId));
    }

    // Flow 상태는 ACTIVE와 INACTIVE 사이에서만 변경합니다.
    @Transactional
    public FlowResponse changeActivationStatus(
            Long groupId,
            Long userId,
            Long flowId,
            FlowStatusChangeRequest request) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MANAGER);
        validateRequest(request);
        Flow flow = oneFlow(groupId, flowId);
        flow.changeActivationStatus(request.status());
        return toResponse(flow);
    }

    // 실행 여부와 관계없이 사용하지 않는 Flow를 휴지통으로 보냅니다.
    @Transactional
    public FlowResponse archive(Long groupId, Long userId, Long flowId) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MANAGER);
        Flow flow = oneFlow(groupId, flowId);
        flow.archive();
        return toResponse(flow);
    }

    // 휴지통에 있는 Flow만 삭제합니다.
    @Transactional
    public void delete(Long groupId, Long userId, Long flowId) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MANAGER);
        Flow flow = oneFlow(groupId, flowId);
        if (!flow.getStatus().equals(FlowStatus.ARCHIVED)) {
            throw new FlowDeletionNotAllowedException(flowId, flow.getStatus());
        }
        linkRepository.deleteByFlowId(flowId);
        nodeRepository.deleteByFlowId(flowId);
        flowRepository.delete(flow);
    }

    // 기존 Flow는 보관하고 수정한 Flow는 새로 저장합니다.
    @Transactional
    public FlowResponse update(Long groupId, Long userId, Long flowId, FlowUpdateRequest request) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MANAGER);
        validateRequest(request);
        Flow currentFlow = oneFlow(groupId, flowId);
        if (currentFlow.getStatus().equals(FlowStatus.ARCHIVED)) {
            throw new InvalidFlowStatusTransitionException(FlowStatus.ARCHIVED, FlowStatus.INACTIVE);
        }
        validateConfiguration(request);
        Flow updateFlow = new Flow(
                groupId,
                currentFlow.getLocationId(),
                request.name(),
                request.description(),
                FlowStatus.INACTIVE);
        validate(updateFlow);
        Flow savedFlow = flowRepository.save(updateFlow);
        Map<String, Long> nodeIds = saveNodes(savedFlow.getId(), request.nodes());
        saveLinks(savedFlow.getId(), request.links(), nodeIds);
        currentFlow.archive();
        return toResponse(savedFlow);
    }

    // 휴지통의 Flow를 새로 만들지 않고 기존 ID 그대로 복구합니다.
    @Transactional
    public FlowResponse restore(Long groupId, Long userId, Long archivedFlowId) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MANAGER);
        Flow archivedFlow = oneFlow(groupId, archivedFlowId);
        archivedFlow.restore();
        return toResponse(archivedFlow);
    }

    // 같은 그룹과 장소에 같은 이름이 있는지 확인합니다.
    private void validate(Flow flow) {
        boolean nameExist = flowRepository.existsByGroupIdAndLocationIdAndName(
                flow.getGroupId(),
                flow.getLocationId(),
                flow.getName());
        if (nameExist) {
            throw new DuplicateFlowNameException(flow.getGroupId(), flow.getLocationId(), flow.getName());
        }
    }

    // 요청값 자체가 없는 경우를 확인합니다.
    private void validateRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("입력값은 null이면 안됩니다.");
        }
    }

    private void validateConfiguration(FlowUpdateRequest request) {
        if (request.nodes() == null || request.nodes().isEmpty()) {
            throw new IllegalArgumentException("Node 목록은 비어 있을 수 없습니다.");
        }
        if (request.links() == null || request.links().isEmpty()) {
            throw new IllegalArgumentException("Link 목록은 비어 있을 수 없습니다.");
        }

        Map<String, FlowNodeRequest> nodesByKey = new HashMap<>();
        for (FlowNodeRequest node : request.nodes()) {
            if (node == null
                    || node.clientNodeKey() == null
                    || node.clientNodeKey().isBlank()
                    || node.nodeType() == null
                    || node.configuration() == null) {
                throw new IllegalArgumentException("Node 요청값이 올바르지 않습니다.");
            }
            if (nodesByKey.putIfAbsent(node.clientNodeKey(), node) != null) {
                throw new IllegalArgumentException("clientNodeKey는 중복될 수 없습니다.");
            }
        }

        long triggerCount = request.nodes().stream()
                .filter(node -> node.nodeType().getCategory() == NodeType.Category.TRIGGER)
                .count();
        long actionCount = request.nodes().stream()
                .filter(node -> node.nodeType().getCategory() == NodeType.Category.ACTION)
                .count();
        if (triggerCount != 1L || actionCount == 0L) {
            throw new IllegalArgumentException(
                    "Trigger Node는 하나이고 Action Node는 하나 이상이어야 합니다.");
        }

        Map<String, List<String>> targetsBySource = new HashMap<>();
        Map<String, Integer> incomingCounts = new HashMap<>();
        request.nodes().forEach(node -> {
            targetsBySource.put(node.clientNodeKey(), new ArrayList<>());
            incomingCounts.put(node.clientNodeKey(), 0);
        });
        Set<String> sourcePorts = new HashSet<>();

        for (FlowLinkRequest link : request.links()) {
            validateLink(link, nodesByKey, sourcePorts);
            targetsBySource.get(link.sourceClientNodeKey()).add(link.targetClientNodeKey());
            incomingCounts.compute(link.targetClientNodeKey(), (key, count) -> count + 1);
        }

        validateConnectionStructure(nodesByKey, targetsBySource, incomingCounts);
    }

    private void validateLink(
            FlowLinkRequest link,
            Map<String, FlowNodeRequest> nodesByKey,
            Set<String> sourcePorts) {
        if (link == null
                || link.sourceClientNodeKey() == null
                || link.targetClientNodeKey() == null
                || link.sourcePort() == null
                || link.sourcePort().isBlank()
                || link.targetPort() == null
                || link.targetPort().isBlank()) {
            throw new IllegalArgumentException("Link 요청값이 올바르지 않습니다.");
        }
        FlowNodeRequest source = nodesByKey.get(link.sourceClientNodeKey());
        FlowNodeRequest target = nodesByKey.get(link.targetClientNodeKey());
        if (source == null || target == null) {
            throw new IllegalArgumentException("Link가 존재하지 않는 Node를 참조합니다.");
        }
        if (link.sourceClientNodeKey().equals(link.targetClientNodeKey())) {
            throw new IllegalArgumentException("Node는 자기 자신과 연결할 수 없습니다.");
        }
        if (source.nodeType().getCategory() == NodeType.Category.ACTION
                || target.nodeType().getCategory() == NodeType.Category.TRIGGER) {
            throw new IllegalArgumentException("Node 연결 방향이 올바르지 않습니다.");
        }
        if (!source.nodeType().getPortSchema().outputPorts(null).contains(link.sourcePort())) {
            throw new IllegalArgumentException("Source Port가 Node Type과 맞지 않습니다.");
        }
        if (!TARGET_PORT.equals(link.targetPort())) {
            throw new IllegalArgumentException("Target Port는 in이어야 합니다.");
        }
        if (!sourcePorts.add(link.sourceClientNodeKey() + "\u0000" + link.sourcePort())) {
            throw new IllegalArgumentException("Source Node Port는 한 Link에만 사용할 수 있습니다.");
        }
    }

    private void validateConnectionStructure(
            Map<String, FlowNodeRequest> nodesByKey,
            Map<String, List<String>> targetsBySource,
            Map<String, Integer> incomingCounts) {
        String triggerKey = nodesByKey.values().stream()
                .filter(node -> node.nodeType().getCategory() == NodeType.Category.TRIGGER)
                .findFirst()
                .orElseThrow()
                .clientNodeKey();

        for (FlowNodeRequest node : nodesByKey.values()) {
            boolean action = node.nodeType().getCategory() == NodeType.Category.ACTION;
            if (action && !targetsBySource.get(node.clientNodeKey()).isEmpty()) {
                throw new IllegalArgumentException("Action Node는 출력 Link를 가질 수 없습니다.");
            }
            if (!action && targetsBySource.get(node.clientNodeKey()).isEmpty()) {
                throw new IllegalArgumentException("Action이 아닌 Node는 출력 Link가 필요합니다.");
            }
        }

        Set<String> reachable = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(triggerKey);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (reachable.add(current)) {
                pending.addAll(targetsBySource.get(current));
            }
        }
        if (reachable.size() != nodesByKey.size()) {
            throw new IllegalArgumentException("Trigger에서 도달할 수 없는 Node가 있습니다.");
        }

        ArrayDeque<String> roots = new ArrayDeque<>();
        incomingCounts.forEach((key, count) -> {
            if (count == 0) {
                roots.add(key);
            }
        });
        int visited = 0;
        while (!roots.isEmpty()) {
            String current = roots.removeFirst();
            visited++;
            for (String target : targetsBySource.get(current)) {
                int remaining = incomingCounts.compute(target, (key, count) -> count - 1);
                if (remaining == 0) {
                    roots.add(target);
                }
            }
        }
        if (visited != nodesByKey.size()) {
            throw new IllegalArgumentException("Flow의 Node와 Link 연결에 Cycle이 있습니다.");
        }
    }

    private Map<String, Long> saveNodes(Long flowId, List<FlowNodeRequest> requests) {
        Map<String, Long> nodeIds = new HashMap<>();
        for (FlowNodeRequest request : requests) {
            Node savedNode = nodeRepository.save(
                    new Node(flowId, request.nodeType(), request.configuration().deepCopy()));
            nodeIds.put(request.clientNodeKey(), savedNode.getId());
        }
        return nodeIds;
    }

    private void saveLinks(
            Long flowId,
            List<FlowLinkRequest> requests,
            Map<String, Long> nodeIds) {
        for (FlowLinkRequest request : requests) {
            linkRepository.save(new Link(
                    flowId,
                    nodeIds.get(request.sourceClientNodeKey()),
                    request.sourcePort(),
                    nodeIds.get(request.targetClientNodeKey()),
                    request.targetPort()));
        }
    }

    // Entity 변환 책임을 DTO 밖에 두고 API에 필요한 Flow 값만 전달합니다.
    private FlowResponse toResponse(Flow flow) {
        return FlowResponse.builder()
                .flowId(flow.getId())
                .groupId(flow.getGroupId())
                .locationId(flow.getLocationId())
                .name(flow.getName())
                .description(flow.getDescription())
                .status(flow.getStatus())
                .createdAt(flow.getCreatedDate())
                .build();
    }
}
