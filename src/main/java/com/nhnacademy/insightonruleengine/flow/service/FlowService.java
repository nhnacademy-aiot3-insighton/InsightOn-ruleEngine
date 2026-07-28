package com.nhnacademy.insightonruleengine.flow.service;

import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.dto.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.dto.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowUpdateRequest;
import com.nhnacademy.insightonruleengine.flow.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowDeletionNotAllowedException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowNotFoundException;
import com.nhnacademy.insightonruleengine.flow.exception.InvalidFlowStatusTransitionException;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlowService {

    private final FlowRepository flowRepository;

    // 새 Flow는 바로 실행되지 않도록 INACTIVE 상태로 저장합니다.
    @Transactional
    public FlowResponse create(Long groupId, FlowCreateRequest request) {
        validateRequest(request);
        Flow flow = new Flow(
                groupId,
                request.locationId(),
                request.name(),
                request.description(),
                FlowStatus.INACTIVE);
        validate(flow);
        return FlowResponse.from(flowRepository.save(flow));
    }

    // 일반 목록에서는 휴지통의 Flow를 제외합니다.
    public List<FlowResponse> findAll(Long groupId) {
        return flowRepository.findAllByGroupIdAndStatusNot(groupId, FlowStatus.ARCHIVED)
                .stream()
                .map(FlowResponse::from)
                .toList();
    }

    // 선택한 상태의 Flow만 조회합니다.
    public List<FlowResponse> findAll(Long groupId, FlowStatus status) {
        return flowRepository.findAllByGroupIdAndStatus(groupId, status)
                .stream()
                .map(FlowResponse::from)
                .toList();
    }

    // 선택한 그룹, 장소, 상태에 맞는 Flow만 조회합니다.
    public List<FlowResponse> findAll(Long groupId, Long locationId, FlowStatus status) {
        return flowRepository.findAllByGroupIdAndLocationIdAndStatus(groupId, locationId, status)
                .stream()
                .map(FlowResponse::from)
                .toList();
    }

    // 요청한 그룹에 속한 Flow의 상세 정보를 반환합니다.
    public FlowResponse findById(Long groupId, Long flowId) {
        return FlowResponse.from(oneFlow(groupId, flowId));
    }

    // Flow가 없거나 다른 그룹의 Flow이면 같은 예외를 발생시킵니다.
    private Flow oneFlow(Long groupId, Long flowId) {
        return flowRepository.findById(flowId)
                .filter(flow -> flow.getGroupId().equals(groupId))
                .orElseThrow(() -> new FlowNotFoundException(groupId, flowId));
    }

    // Flow 상태는 ACTIVE와 INACTIVE 사이에서만 변경합니다.
    @Transactional
    public FlowResponse changeActivationStatus(Long groupId, Long flowId, FlowStatusChangeRequest request) {
        validateRequest(request);
        Flow flow = oneFlow(groupId, flowId);
        flow.changeActivationStatus(request.status());
        return FlowResponse.from(flow);
    }

    // 휴지통에 있는 Flow만 삭제합니다.
    @Transactional
    public void delete(Long groupId, Long flowId) {
        Flow flow = oneFlow(groupId, flowId);
        if (!flow.getStatus().equals(FlowStatus.ARCHIVED)) {
            throw new FlowDeletionNotAllowedException(flowId, flow.getStatus());
        }
        flowRepository.delete(flow);
    }

    // 기존 Flow는 보관하고 수정한 Flow는 새로 저장합니다.
    @Transactional
    public FlowResponse update(Long groupId, Long flowId, FlowUpdateRequest request) {
        validateRequest(request);
        Flow currentFlow = oneFlow(groupId, flowId);
        if (currentFlow.getStatus().equals(FlowStatus.ARCHIVED)) {
            throw new InvalidFlowStatusTransitionException(FlowStatus.ARCHIVED, FlowStatus.INACTIVE);
        }
        Flow updateFlow = new Flow(
                groupId,
                currentFlow.getLocationId(),
                request.name(),
                request.description(),
                FlowStatus.INACTIVE);
        validate(updateFlow);
        currentFlow.archive();
        return FlowResponse.from(flowRepository.save(updateFlow));
    }

    // 휴지통의 Flow를 새로 만들지 않고 기존 ID 그대로 복구합니다.
    @Transactional
    public FlowResponse restore(Long groupId, Long archivedFlowId) {
        Flow archivedFlow = oneFlow(groupId, archivedFlowId);
        archivedFlow.restore();
        return FlowResponse.from(archivedFlow);
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
}
