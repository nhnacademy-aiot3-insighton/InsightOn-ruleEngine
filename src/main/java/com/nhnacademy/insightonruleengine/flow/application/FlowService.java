package com.nhnacademy.insightonruleengine.flow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nhnacademy.insightonruleengine.client.core.CoreActuatorClient;
import com.nhnacademy.insightonruleengine.client.core.LocationResponse;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowUpdateRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.response.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.application.assembly.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.application.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.application.authorization.GroupRole;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowActivationValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowStructureValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.NodeConfigurationValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.Link;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.FlowDeletionNotAllowedException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.FlowNotFoundException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.ForbiddenException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.InvalidAiDraftNameException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.InvalidFlowStatusTransitionException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.InvalidFlowStructureException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.LocationNotFoundException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.ReservedFlowNamePrefixException;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.NodeRepository;
import com.nhnacademy.insightonruleengine.runner.application.schedule.ScheduleFlowScheduler;
import com.nhnacademy.insightonruleengine.runner.infrastructure.cache.ActiveFlowDefinitionProvider;
import feign.FeignException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    // AI가 리포트 분석·챗봇 요청 결과로 자동화 flow 초안을 요청합니다. 유저가 없어 MANAGER 권한
    // 체크는 하지 않습니다. AI 쪽은 같은 위치·지표에 항상 같은 이름을 보내므로, 이름이 일치하는
    // "살아있는"(ARCHIVED가 아닌) Flow를 찾아 내용까지 같으면 그대로 반환하고, cron이나 액추에이터
    // 명령이 달라졌으면 기존 것을 archive하고 새로 만듭니다.
    @Transactional
    public FlowResponse createAiDraft(Long groupId, FlowCreateRequest request) {
        validateRequest(request);
        validateAiDraftName(request.name());
        validateStructure(request.nodes(), request.links());

        Optional<Flow> existingFlow = flowRepository.findByGroupIdAndLocationIdAndNameAndStatusNot(
                groupId, request.locationId(), request.name(), FlowStatus.ARCHIVED);
        if (existingFlow.isPresent()) {
            ContentDiff diff = diffContent(existingFlow.get(), request);
            if (diff == ContentDiff.SAME) {
                log.info(
                        "AI draft 내용 변경이 없어 그대로 반환합니다. flowId={}, status={}",
                        existingFlow.get().getId(),
                        existingFlow.get().getStatus());
                return toResponse(existingFlow.get(), null);
            }
            return replaceAiDraft(groupId, request, existingFlow.get(), diff);
        }

        LocationResponse location = fetchLocationSafely(request.locationId());
        validateLocationOwnership(groupId, location);

        Flow savedFlow = saveAiDraftFlow(groupId, request);
        Map<String, Long> nodeIds = saveNodes(savedFlow.getId(), request.nodes());
        saveLinks(savedFlow.getId(), request.links(), nodeIds);

        if (isAiDirectMode(location)) {
            activateAiDraft(savedFlow);
        }
        log.info("AI draft를 생성했습니다. flowId={}, status={}", savedFlow.getId(), savedFlow.getStatus());
        return toResponse(savedFlow, null);
    }

    // 이름은 같지만 내용(cron 또는 액추에이터 명령)이 달라진 기존 AI draft를 archive하고 같은 이름으로
    // 새로 만듭니다. 두 Flow가 같은 이름을 동시에 가질 수 없으므로(부분 유니크 인덱스가 ARCHIVED만
    // 제외), 기존 Flow의 archive를 먼저 flush해 DB에 반영한 뒤에 새 Flow를 insert해야 합니다.
    private FlowResponse replaceAiDraft(
            Long groupId,
            FlowCreateRequest request,
            Flow existingFlow,
            ContentDiff diff) {
        LocationResponse location = fetchLocationSafely(request.locationId());
        validateLocationOwnership(groupId, location);

        boolean wasActive = existingFlow.getStatus() == FlowStatus.ACTIVE;
        Long replacedFlowId = existingFlow.getId();
        existingFlow.archive();
        flowRepository.saveAndFlush(existingFlow);
        activeFlowDefinitionProvider.refreshAfterCommit(existingFlow.getGroupId(), existingFlow.getLocationId());
        scheduleFlowScheduler.cancelAfterCommit(existingFlow.getId());

        Flow savedFlow = saveAiDraftFlow(groupId, request);
        Map<String, Long> nodeIds = saveNodes(savedFlow.getId(), request.nodes());
        saveLinks(savedFlow.getId(), request.links(), nodeIds);

        // cron만 바뀐 경우는 사용자가 승인한 자동화의 미세 조정이라 켜져 있던 상태를 그대로 이어간다.
        // 액추에이터 명령 자체가 바뀌면 물리적으로 수행하는 동작이 달라지는 것이므로 재승인을 받는다.
        // AI_DIRECT 위치는 두 경우 모두 기존 로직대로 무조건 자동 활성화한다.
        boolean shouldActivate = isAiDirectMode(location) || (diff == ContentDiff.CRON_ONLY && wasActive);
        if (shouldActivate) {
            activateAiDraft(savedFlow);
        }
        log.info(
                "AI draft 내용이 달라져 archive 후 재생성했습니다. flowId={}, status={}, replacedFlowId={}",
                savedFlow.getId(), savedFlow.getStatus(), replacedFlowId);
        return toResponse(savedFlow, replacedFlowId);
    }

    private Flow saveAiDraftFlow(Long groupId, FlowCreateRequest request) {
        Flow flow = new Flow(
                groupId,
                request.locationId(),
                request.name(),
                request.description(),
                FlowStatus.INACTIVE);
        try {
            return flowRepository.save(flow);
        } catch (DataIntegrityViolationException exception) {
            // 이름 유니크 제약상 이 요청과 완전히 같은 자동화가 이미 존재한다는 뜻입니다. 동시에 들어온
            // 같은 요청이 먼저 저장을 마친 경우이므로, 재조회를 시도하지 않고 그대로 충돌로 응답합니다.
            // 이 엔드포인트를 호출하는 쪽은 AI뿐이고 이름을 항상 같은 규칙으로 만들기 때문에, 여기서
            // 발생하는 409는 "이미 존재함" 외의 다른 의미를 가질 수 없습니다.
            throw new DuplicateFlowNameException(groupId, request.locationId(), request.name());
        }
    }

    private enum ContentDiff {
        SAME, CRON_ONLY, ACTUATOR_CHANGED
    }

    // 근사 매칭 없이 완전 일치만 본다. AI가 cron의 분을 항상 고정값으로 만들어서, cron 문자열이
    // 다르다는 건 항상 실제로 다른 시간을 의미하기 때문에 tolerance를 둘 필요가 없다.
    private ContentDiff diffContent(Flow existingFlow, FlowCreateRequest request) {
        List<Node> existingNodes = nodeRepository.findByFlowId(existingFlow.getId());

        ActuatorControlSpec existingAction = extractActuatorControl(existingNodes);
        ActuatorControlSpec newAction = extractActuatorControlFromRequest(request.nodes());
        if (!Objects.equals(existingAction, newAction)) {
            return ContentDiff.ACTUATOR_CHANGED;
        }

        String existingCron = extractScheduleCron(existingNodes);
        String newCron = extractScheduleCronFromRequest(request.nodes());
        if (!Objects.equals(existingCron, newCron)) {
            return ContentDiff.CRON_ONLY;
        }

        return ContentDiff.SAME;
    }

    private record ActuatorControlSpec(String actuatorType, String command, String commandValue) {
    }

    private ActuatorControlSpec extractActuatorControl(List<Node> nodes) {
        return nodes.stream()
                .filter(node -> node.getNodeType() == NodeType.ACTUATOR_CONTROL)
                .findFirst()
                .map(node -> toActuatorControlSpec(node.getConfiguration()))
                .orElse(null);
    }

    private ActuatorControlSpec extractActuatorControlFromRequest(List<FlowNodeRequest> nodes) {
        return nodes.stream()
                .filter(node -> node.nodeType() == NodeType.ACTUATOR_CONTROL)
                .findFirst()
                .map(node -> toActuatorControlSpec(node.configuration()))
                .orElse(null);
    }

    private ActuatorControlSpec toActuatorControlSpec(JsonNode configuration) {
        return new ActuatorControlSpec(
                textValue(configuration, "actuatorType"),
                textValue(configuration, "command"),
                textValue(configuration, "commandValue"));
    }

    private String extractScheduleCron(List<Node> nodes) {
        return nodes.stream()
                .filter(node -> node.getNodeType() == NodeType.SCHEDULE)
                .findFirst()
                .map(node -> textValue(node.getConfiguration(), "cron"))
                .orElse(null);
    }

    private String extractScheduleCronFromRequest(List<FlowNodeRequest> nodes) {
        return nodes.stream()
                .filter(node -> node.nodeType() == NodeType.SCHEDULE)
                .findFirst()
                .map(node -> textValue(node.configuration(), "cron"))
                .orElse(null);
    }

    private String textValue(JsonNode configuration, String field) {
        JsonNode value = configuration.get(field);
        return value == null || value.isNull() ? null : value.asText();
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

    // Core에 위치 정보를 확인합니다. 404는 locationId가 존재하지 않는다는 확정된 답이므로 그대로
    // 거부합니다. 그 외 조회 실패는 일시적일 수 있으므로 null을 반환해 안전하게 대기(SUGGESTION)로
    // 취급하게 합니다 — "확정된 문제는 막고, 불확실하면 보수적으로 진행한다"는 원칙을 따릅니다.
    private LocationResponse fetchLocationSafely(Long locationId) {
        try {
            return coreActuatorClient.getLocation(locationId);
        } catch (FeignException.NotFound exception) {
            throw new LocationNotFoundException(locationId);
        } catch (RuntimeException exception) {
            // FeignException뿐 아니라 응답 디코딩 실패 등 Core 위치 조회 과정에서 생길 수 있는 예외를
            // 전부 안전하게 실패로 취급합니다. 이 조회 하나 때문에 draft 생성 자체가 실패하면 안 됩니다.
            log.warn(
                    "Core 위치 조회에 실패해 INACTIVE로 생성합니다. locationId={}",
                    locationId,
                    exception);
            return null;
        }
    }

    // Core가 확인해준 위치가 실제로는 다른 그룹 소유라면, 잘못된 groupId/locationId 조합으로 다른
    // 그룹의 위치에 Flow가 만들어지는 걸 막기 위해 요청 자체를 거부합니다(테넌트 경계 보호).
    // 조회 자체가 실패해 location이 null이면 확정된 정보가 없으므로 여기서는 막지 않습니다.
    private void validateLocationOwnership(Long groupId, LocationResponse location) {
        if (location != null && !groupId.equals(location.groupId())) {
            throw new ForbiddenException(
                    "Core 응답의 groupId가 요청과 다릅니다. requested:" + groupId + ", returned:" + location.groupId());
        }
    }

    // 위치가 AI 자동 실행(AI_DIRECT)으로 설정돼 있는지 확인합니다. 조회 실패로 location이 없으면 대기(SUGGESTION)로 취급합니다.
    private boolean isAiDirectMode(LocationResponse location) {
        return location != null && location.autoControlMode() == LocationResponse.AutoControlMode.AI_DIRECT;
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
        validateRestoreNameAvailable(archivedFlow);
        archivedFlow.restore();
        return toResponse(archivedFlow);
    }

    // ARCHIVED가 아닌 동안(즉 archive된 채로 있던 사이) 같은 이름의 새 Flow가 만들어졌을 수 있어,
    // 복구 전에 이름 충돌을 확인합니다. (group_id, location_id, name) 유니크 인덱스가 ARCHIVED를
    // 제외하므로 DB가 이 충돌을 막아주지 않습니다 — AI draft 갱신(archive 후 같은 이름 재생성)이
    // 정상적으로 이 상황을 만들 수 있습니다.
    private void validateRestoreNameAvailable(Flow archivedFlow) {
        boolean nameTaken = flowRepository.existsByGroupIdAndLocationIdAndNameAndStatusNot(
                archivedFlow.getGroupId(),
                archivedFlow.getLocationId(),
                archivedFlow.getName(),
                FlowStatus.ARCHIVED);
        if (nameTaken) {
            throw new DuplicateFlowNameException(
                    archivedFlow.getGroupId(), archivedFlow.getLocationId(), archivedFlow.getName());
        }
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
        return toResponse(flow, null);
    }

    private FlowResponse toResponse(Flow flow, Long replacedFlowId) {
        return FlowResponse.builder()
                .flowId(flow.getId())
                .groupId(flow.getGroupId())
                .locationId(flow.getLocationId())
                .name(flow.getName())
                .description(flow.getDescription())
                .status(flow.getStatus())
                .createdAt(flow.getCreatedDate())
                .replacedFlowId(replacedFlowId)
                .build();
    }
}
