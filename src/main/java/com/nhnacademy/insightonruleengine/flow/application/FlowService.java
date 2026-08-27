package com.nhnacademy.insightonruleengine.flow.application;

import com.nhnacademy.insightonruleengine.flow.application.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.application.authorization.GroupRole;
import com.nhnacademy.insightonruleengine.runner.infrastructure.cache.ActiveFlowDefinitionProvider;
import com.nhnacademy.insightonruleengine.runner.application.schedule.ScheduleFlowCoordinator;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.Link;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.application.assembly.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.response.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowUpdateRequest;
import com.nhnacademy.insightonruleengine.flow.domain.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.FlowDeletionNotAllowedException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.FlowNotFoundException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.InvalidFlowStatusTransitionException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.InvalidFlowStructureException;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.NodeRepository;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowActivationValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowStructureValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.NodeConfigurationValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureValidationError;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final FlowStructureValidator flowStructureValidator;
    private final NodeConfigurationValidator nodeConfigurationValidator;
    private final FlowDefinitionAssembler flowDefinitionAssembler;
    private final FlowActivationValidator flowActivationValidator;
    private final ActiveFlowDefinitionProvider activeFlowDefinitionProvider;
    private final ScheduleFlowCoordinator scheduleFlowCoordinator;

    // 새 Flow는 바로 실행되지 않도록 INACTIVE 상태로 저장합니다.
    @Transactional
    public FlowResponse create(Long groupId, Long userId, FlowCreateRequest request) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MANAGER);
        validateRequest(request);
        validateStructure(request.nodes(), request.links());
        Flow flow = new Flow(
                groupId,
                request.locationId(),
                request.name(),
                request.description(),
                FlowStatus.INACTIVE);
        validate(flow);
        Flow savedFlow = flowRepository.save(flow);
        Map<String, Long> nodeIds = saveNodes(savedFlow.getId(), request.nodes());
        saveLinks(savedFlow.getId(), request.links(), nodeIds);
        return toResponse(savedFlow);
    }

    // 일반 목록에서는 휴지통의 Flow를 제외합니다.
    public List<FlowResponse> findAllUnarchivedFlows(Long groupId, Long userId) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MEMBER);
        return flowRepository.findAllByGroupIdAndStatusNot(groupId, FlowStatus.ARCHIVED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 선택한 상태의 Flow만 조회합니다.
    public List<FlowResponse> findByGroupIdAndStatus(Long groupId, Long userId, FlowStatus status) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MEMBER);
        return flowRepository.findAllByGroupIdAndStatus(groupId, status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 선택한 그룹, 장소, 상태에 맞는 Flow만 조회합니다.
    public List<FlowResponse> findByGroupIdAndLocationIdAndStatus(
            Long groupId,
            Long userId,
            Long locationId,
            FlowStatus status) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MEMBER);
        return flowRepository.findAllByGroupIdAndLocationIdAndStatus(groupId, locationId, status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 요청한 그룹에 속한 Flow의 상세 정보를 반환합니다.
    public FlowResponse findById(Long groupId, Long userId, Long flowId) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MEMBER);
        return toResponse(getFlow(groupId, flowId));
    }

    // 플로우 하나 조회, validation처리
    private Flow getFlow(Long groupId, Long flowId) {
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
        Flow flow = getFlow(groupId, flowId);
        if (request.status() == FlowStatus.ACTIVE) {
            List<FlowStructureValidationError> errors = flowActivationValidator.validate(
                    flowDefinitionAssembler.assemble(groupId, flowId));
            if (errors != null && !errors.isEmpty()) {
                throw new InvalidFlowStructureException(errors);
            }
        }
        flow.changeActivationStatus(request.status());
        activeFlowDefinitionProvider.refreshAfterCommit(flow.getGroupId(), flow.getLocationId());
        if (request.status() == FlowStatus.ACTIVE) {
            scheduleFlowCoordinator.registerAfterCommit(flow.getGroupId(), flow.getId());
        } else {
            scheduleFlowCoordinator.cancelAfterCommit(flow.getId());
        }
        return toResponse(flow);
    }

    // 실행 여부와 관계없이 사용하지 않는 Flow를 휴지통으로 보냅니다.
    @Transactional
    public FlowResponse archive(Long groupId, Long userId, Long flowId) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MANAGER);
        Flow flow = getFlow(groupId, flowId);
        flow.archive();
        activeFlowDefinitionProvider.refreshAfterCommit(flow.getGroupId(), flow.getLocationId());
        scheduleFlowCoordinator.cancelAfterCommit(flow.getId());
        return toResponse(flow);
    }

    // 휴지통에 있는 Flow만 삭제합니다.
    @Transactional
    public void delete(Long groupId, Long userId, Long flowId) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MANAGER);
        Flow flow = getFlow(groupId, flowId);
        if (!flow.getStatus().equals(FlowStatus.ARCHIVED)) {
            throw new FlowDeletionNotAllowedException(flowId, flow.getStatus());
        }
        linkRepository.deleteByFlowId(flowId);
        nodeRepository.deleteByFlowId(flowId);
        flowRepository.delete(flow);
        activeFlowDefinitionProvider.refreshAfterCommit(flow.getGroupId(), flow.getLocationId());
        scheduleFlowCoordinator.cancelAfterCommit(flow.getId());
    }

    // 기존 Flow는 보관하고 수정한 Flow는 새로 저장합니다.
    @Transactional
    public FlowResponse update(Long groupId, Long userId, Long flowId, FlowUpdateRequest request) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MANAGER);
        validateRequest(request);
        Flow currentFlow = getFlow(groupId, flowId);
        if (currentFlow.getStatus().equals(FlowStatus.ARCHIVED)) {
            throw new InvalidFlowStatusTransitionException(FlowStatus.ARCHIVED, FlowStatus.INACTIVE);
        }
        validateStructure(request.nodes(), request.links());

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
        activeFlowDefinitionProvider.refreshAfterCommit(currentFlow.getGroupId(), currentFlow.getLocationId());
        scheduleFlowCoordinator.cancelAfterCommit(currentFlow.getId());
        return toResponse(savedFlow);
    }

    // 휴지통의 Flow를 새로 만들지 않고 기존 ID 그대로 복구합니다.
    @Transactional
    public FlowResponse restore(Long groupId, Long userId, Long archivedFlowId) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MANAGER);
        Flow archivedFlow = getFlow(groupId, archivedFlowId);
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

    private void validateStructure(List<FlowNodeRequest> nodes, List<FlowLinkRequest> links) {
        List<FlowStructureValidationError> errors = new ArrayList<>();
        addErrors(errors, flowStructureValidator.validate(nodes, links));
        addErrors(errors, nodeConfigurationValidator.validate(nodes));
        if (!errors.isEmpty()) {
            throw new InvalidFlowStructureException(errors);
        }
    }

    private void addErrors(
            List<FlowStructureValidationError> target,
            List<FlowStructureValidationError> source
    ) {
        if (source != null) {
            target.addAll(source);
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
