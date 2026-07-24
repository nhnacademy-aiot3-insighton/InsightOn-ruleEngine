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

    // 새 Flow는 실행되지 않는 상태로 저장해 명시적인 활성화 전 실행되는 일을 막는다.
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

    // 일반 목록에서 보관된 Flow를 숨겨 휴지통 항목이 현재 Flow처럼 보이지 않게 한다.
    public List<FlowResponse> findAll(Long groupId) {
        return flowRepository.findAllByGroupIdAndStatusNot(groupId, FlowStatus.ARCHIVED)
                .stream()
                .map(FlowResponse::from)
                .toList();
    }

    // 휴지통을 포함해 사용자가 명시한 한 상태의 Flow만 조회한다.
    public List<FlowResponse> findAll(Long groupId, FlowStatus status) {
        return flowRepository.findAllByGroupIdAndStatus(groupId, status)
                .stream()
                .map(FlowResponse::from)
                .toList();
    }

    // 그룹·장소·상태 범위를 저장소에서 제한해 실행 Route 조회에도 재사용한다.
    public List<FlowResponse> findAll(Long groupId, Long locationId, FlowStatus status) {
        return flowRepository.findAllByGroupIdAndLocationIdAndStatus(groupId, locationId, status)
                .stream()
                .map(FlowResponse::from)
                .toList();
    }

    // 보관된 이력까지 같은 상세 응답으로 읽되 요청 그룹의 소유권은 확인한다.
    public FlowResponse findById(Long groupId, Long flowId) {
        return FlowResponse.from(oneFlow(groupId, flowId));
    }

    // 미존재와 다른 그룹 접근을 같은 실패로 처리해 다른 그룹의 Flow 정보를 숨긴다.
    private Flow oneFlow(Long groupId, Long flowId) {
        return flowRepository.findById(flowId)
                .filter(flow -> flow.getGroupId().equals(groupId))
                .orElseThrow(() -> new FlowNotFoundException(groupId, flowId));
    }

    // 상태 버튼으로 ACTIVE와 INACTIVE만 오가게 해 보관 상태가 우회 생성되지 않게 한다.
    @Transactional
    public FlowResponse changeActivationStatus(Long groupId, Long flowId, FlowStatusChangeRequest request) {
        validateRequest(request);
        Flow flow = oneFlow(groupId, flowId);
        flow.changeActivationStatus(request.status());
        return FlowResponse.from(flow);
    }

    // JPA 생명주기를 거치도록 보관된 Entity만 로드한 뒤 삭제한다.
    @Transactional
    public void delete(Long groupId, Long flowId) {
        Flow flow = oneFlow(groupId, flowId);
        if (!flow.getStatus().equals(FlowStatus.ARCHIVED)) {
            throw new FlowDeletionNotAllowedException(flowId, flow.getStatus());
        }
        flowRepository.delete(flow);
    }

    // 기존 Flow는 이력으로 보관하고 같은 장소에 새 이름의 수정본을 저장한다.
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

    // 선택한 보관 Flow 자체를 되살려 기존 ID와 향후 연결될 Graph를 유지한다.
    @Transactional
    public FlowResponse restore(Long groupId, Long archivedFlowId) {
        Flow archivedFlow = oneFlow(groupId, archivedFlowId);
        archivedFlow.restore();
        return FlowResponse.from(archivedFlow);
    }

    // 보관 상태까지 이름을 점유하게 해 저장소 UNIQUE 계약과 같은 중복 규칙을 적용한다.
    private void validate(Flow flow) {
        boolean nameExist = flowRepository.existsByGroupIdAndLocationIdAndName(
                flow.getGroupId(),
                flow.getLocationId(),
                flow.getName());
        if (nameExist) {
            throw new DuplicateFlowNameException(flow.getGroupId(), flow.getLocationId(), flow.getName());
        }
    }

    // HTTP 밖에서 Service를 직접 호출해도 null 요청이 명확한 입력 오류가 되게 한다.
    private void validateRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("입력값은 null이면 안됩니다.");
        }
    }
}
