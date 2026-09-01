package com.nhnacademy.insightonruleengine.flow.application;

import com.nhnacademy.insightonruleengine.client.core.CoreActuatorClient;
import com.nhnacademy.insightonruleengine.client.core.LocationResponse;
import com.nhnacademy.insightonruleengine.flow.application.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.application.authorization.GroupRole;
import com.nhnacademy.insightonruleengine.runner.infrastructure.cache.ActiveFlowDefinitionProvider;
import com.nhnacademy.insightonruleengine.runner.application.schedule.ScheduleFlowScheduler;
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
import com.nhnacademy.insightonruleengine.flow.domain.exception.InvalidAiDraftNameException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.InvalidFlowStatusTransitionException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.InvalidFlowStructureException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.ReservedFlowNamePrefixException;
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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FlowService {

    private static final String TARGET_PORT = "in";
    private static final String AI_DRAFT_NAME_PREFIX = "[AI] ";

    private final FlowRepository flowRepository;
    private final GroupAuthorizationService groupAuthorizationService;
    private final NodeRepository nodeRepository;
    private final LinkRepository linkRepository;
    private final FlowStructureValidator flowStructureValidator;
    private final NodeConfigurationValidator nodeConfigurationValidator;
    private final FlowDefinitionAssembler flowDefinitionAssembler;
    private final FlowActivationValidator flowActivationValidator;
    private final ActiveFlowDefinitionProvider activeFlowDefinitionProvider;
    private final ScheduleFlowScheduler scheduleFlowScheduler;
    private final CoreActuatorClient coreActuatorClient;

    // 새 Flow는 바로 실행되지 않도록 INACTIVE 상태로 저장합니다.
    @Transactional
    public FlowResponse create(Long groupId, Long userId, FlowCreateRequest request) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MANAGER);
        validateRequest(request);
        rejectAiDraftPrefix(request.name());
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

    // AI가 리포트 분석 결과로 자동화 flow 초안을 요청합니다. 유저가 없어 MANAGER 권한 체크는 하지 않습니다.
    @Transactional
    public FlowResponse createAiDraft(Long groupId, FlowCreateRequest request) {
        validateRequest(request);
        validateAiDraftName(request.name());
        validateStructure(request.nodes(), request.links());

        Optional<Flow> existingFlow = flowRepository.findByGroupIdAndLocationIdAndName(
                groupId, request.locationId(), request.name());
        if (existingFlow.isPresent()) {
            log.info(
                    "AI draft가 이미 있어 그대로 반환합니다. flowId={}, status={}",
                    existingFlow.get().getId(),
                    existingFlow.get().getStatus());
            return toResponse(existingFlow.get());
        }

        Flow flow = new Flow(
                groupId,
                request.locationId(),
                request.name(),
                request.description(),
                FlowStatus.INACTIVE);
        Flow savedFlow;
        try {
            savedFlow = flowRepository.save(flow);
        } catch (DataIntegrityViolationException exception) {
            // 이름 유니크 제약상 이 요청과 완전히 같은 자동화가 이미 존재한다는 뜻입니다. 동시에 들어온
            // 같은 요청이 먼저 저장을 마친 경우이므로, 재조회를 시도하지 않고 그대로 충돌로 응답합니다.
            // 이 엔드포인트를 호출하는 쪽은 AI뿐이고 이름을 항상 같은 규칙으로 만들기 때문에, 여기서
            // 발생하는 409는 "이미 존재함" 외의 다른 의미를 가질 수 없습니다.
            throw new DuplicateFlowNameException(groupId, request.locationId(), request.name());
        }
        Map<String, Long> nodeIds = saveNodes(savedFlow.getId(), request.nodes());
        saveLinks(savedFlow.getId(), request.links(), nodeIds);

        if (isAiDirectLocation(groupId, request.locationId())) {
            activateAiDraft(savedFlow);
        }
        log.info("AI draft를 생성했습니다. flowId={}, status={}", savedFlow.getId(), savedFlow.getStatus());
        return toResponse(savedFlow);
    }

    // 이름만 보고도 AI가 만든 Flow인지 구분할 수 있도록, 접두어를 엔진이 직접 강제합니다.
    private void validateAiDraftName(String name) {
        if (name == null || !name.startsWith(AI_DRAFT_NAME_PREFIX)) {
            throw new InvalidAiDraftNameException(AI_DRAFT_NAME_PREFIX, name);
        }
    }

    // 유저가 만든 Flow 이름이 AI draft 전용 접두어를 흉내내 Front에서 잘못 표시되는 것을 막습니다.
    private void rejectAiDraftPrefix(String name) {
        if (name != null && name.startsWith(AI_DRAFT_NAME_PREFIX)) {
            throw new ReservedFlowNamePrefixException(AI_DRAFT_NAME_PREFIX, name);
        }
    }

    // 위치가 AI 자동 실행(AI_DIRECT)으로 설정돼 있는지 Core에 확인합니다. 조회에 실패하면 안전하게 대기(SUGGESTION)로 취급합니다.
    private boolean isAiDirectLocation(Long groupId, Long locationId) {
        try {
            LocationResponse location = coreActuatorClient.getLocation(locationId);
            return location != null
                    && groupId.equals(location.groupId())
                    && location.autoControlMode() == LocationResponse.AutoControlMode.AI_DIRECT;
        } catch (RuntimeException exception) {
            // FeignException뿐 아니라 응답 디코딩 실패 등 Core 위치 조회 과정에서 생길 수 있는 예외를
            // 전부 안전하게 실패로 취급합니다. 이 조회 하나 때문에 draft 생성 자체가 실패하면 안 됩니다.
            log.warn(
                    "AI_DIRECT 여부 확인을 위한 Core 위치 조회에 실패해 INACTIVE로 생성합니다. locationId={}",
                    locationId,
                    exception);
            return false;
        }
    }

    // AI_DIRECT 위치의 draft를 changeActivationStatus와 같은 절차로 ACTIVE 전환합니다. 구조가 실행 불가능하면 INACTIVE로 남깁니다.
    private void activateAiDraft(Flow flow) {
        List<FlowStructureValidationError> errors = flowActivationValidator.validate(
                flowDefinitionAssembler.assemble(flow.getGroupId(), flow.getId()));
        if (errors != null && !errors.isEmpty()) {
            log.warn("AI_DIRECT draft가 실행 조건을 만족하지 못해 INACTIVE로 유지합니다. flowId={}, errors={}",
                    flow.getId(), errors);
            return;
        }
        flow.changeActivationStatus(FlowStatus.ACTIVE);
        activeFlowDefinitionProvider.refreshAfterCommit(flow.getGroupId(), flow.getLocationId());
        scheduleFlowScheduler.registerAfterCommit(flow.getGroupId(), flow.getId());
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
            scheduleFlowScheduler.registerAfterCommit(flow.getGroupId(), flow.getId());
        } else {
            scheduleFlowScheduler.cancelAfterCommit(flow.getId());
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
        scheduleFlowScheduler.cancelAfterCommit(flow.getId());
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
        scheduleFlowScheduler.cancelAfterCommit(flow.getId());
    }

    // 기존 Flow는 보관하고 수정한 Flow는 새로 저장합니다.
    @Transactional
    public FlowResponse update(Long groupId, Long userId, Long flowId, FlowUpdateRequest request) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MANAGER);
        validateRequest(request);
        rejectAiDraftPrefix(request.name());
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
        scheduleFlowScheduler.cancelAfterCommit(currentFlow.getId());
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
