package com.nhnacademy.insightonruleengine.flow.service;

import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.dto.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.dto.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowDeletionNotAllowedException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowNotFoundException;
import com.nhnacademy.insightonruleengine.flow.exception.InvalidFlowRestoreException;
import com.nhnacademy.insightonruleengine.flow.exception.InvalidFlowStatusTransitionException;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlowService {

    private final FlowRepository flowRepository;

    @Transactional
    public FlowResponse create(Long groupId, FlowCreateRequest request) {
        Objects.requireNonNull(request, "입력값은 null이면 안됩니다");
        Flow flow = new Flow(
                groupId,
                request.locationId(),
                request.name(),
                request.description(),
                FlowStatus.INACTIVE
        );
        validate(flow);
        return FlowResponse.from(flowRepository.save(flow));
    }

    public List<FlowResponse> findAll(Long groupId) {
        return flowRepository.findAllByGroupId(groupId)
                .stream()
                .map(FlowResponse::from)
                .toList();
    }

    public List<FlowResponse> findAll(Long groupId, Long locationId) {
        return flowRepository.findAllByGroupIdAndLocationIdAndStatus(groupId, locationId, FlowStatus.ACTIVE)
                .stream()
                .map(FlowResponse::from)
                .toList();
    }

    public List<FlowResponse> findAll(Long groupId, Long locationId, FlowStatus status) {
        return flowRepository.findAllByGroupIdAndLocationIdAndStatus(groupId, locationId, status)
                .stream()
                .map(FlowResponse::from)
                .toList();
    }

    public FlowResponse findById(Long groupId, Long flowId) {
        return FlowResponse.from(oneFlow(groupId, flowId));
    }

    private Flow oneFlow(Long groupId, Long flowId) {
        return flowRepository.findById(flowId)
                .filter(flow -> flow.getGroupId().equals(groupId))
                .orElseThrow(() -> new FlowNotFoundException(groupId, flowId));
    }

    @Transactional
    public FlowResponse changeActivationStatus(Long groupId, Long flowId, FlowStatusChangeRequest request) {
        Objects.requireNonNull(request, "입력값은 null이면 안됩니다.");
        Flow flow = oneFlow(groupId, flowId);
        flow.changeActivationStatus(request.status());
        return FlowResponse.from(flow);
    }

    @Transactional
    public void delete(Long groupId, Long flowId) {
        Flow flow = oneFlow(groupId, flowId);
        if (!flow.getStatus().equals(FlowStatus.ARCHIVED)) {
            throw new FlowDeletionNotAllowedException(flowId, flow.getStatus());
        }
        flowRepository.delete(flow);
    }

    @Transactional
    public FlowResponse update(Long groupId, Long flowId, FlowCreateRequest request) {
        Objects.requireNonNull(request, "입력값은 null이면 안됩니다.");
        Flow currentFlow = oneFlow(groupId, flowId);
        if (currentFlow.getStatus().equals(FlowStatus.ARCHIVED)) {
            throw new InvalidFlowStatusTransitionException(currentFlow.getStatus(), currentFlow.getStatus());
        }
        Flow updateFlow = new Flow(
                groupId,
                request.locationId(),
                request.name(),
                request.description(),
                FlowStatus.INACTIVE
        );
        validate(updateFlow);
        currentFlow.archive();
        return FlowResponse.from(flowRepository.save(updateFlow));
    }

    @Transactional
    public FlowResponse restore(Long groupId, Long currentFlowId, Long archivedFlowId) {
        Flow currentFlow = oneFlow(groupId, currentFlowId);
        Flow archivedFlow = oneFlow(groupId, archivedFlowId);

        boolean sameFlow = currentFlow == archivedFlow
                || (currentFlow.getId() != null && currentFlow.getId().equals(archivedFlow.getId()));
        if (sameFlow) {
            throw new InvalidFlowRestoreException("Current and archived Flow must be different");
        }
        if (!currentFlow.getLocationId().equals(archivedFlow.getLocationId())) {
            throw new InvalidFlowRestoreException("Flows must belong to the same location");
        }
        if (currentFlow.getStatus() == FlowStatus.ARCHIVED) {
            throw new InvalidFlowStatusTransitionException(currentFlow.getStatus(), FlowStatus.ARCHIVED);
        }
        if (archivedFlow.getStatus() != FlowStatus.ARCHIVED) {
            throw new InvalidFlowStatusTransitionException(archivedFlow.getStatus(), FlowStatus.INACTIVE);
        }

        currentFlow.archive();
        archivedFlow.restore();
        return FlowResponse.from(archivedFlow);
    }

    private void validate(Flow flow) {
        boolean nameExit = flowRepository.existsByGroupIdAndLocationIdAndName(flow.getGroupId(), flow.getLocationId(),
                flow.getName());
        if (nameExit) {
            throw new DuplicateFlowNameException(flow.getGroupId(), flow.getLocationId(), flow.getName());
        }
    }

}
