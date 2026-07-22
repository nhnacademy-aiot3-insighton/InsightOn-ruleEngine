package com.nhnacademy.insightonruleengine.flow.service;

import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.dto.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlowService {

    private final FlowRepository flowRepository;

    public FlowResponse create(Long groupId, FlowCreateRequest request) {
        Objects.requireNonNull(request, "request는 null X");
        Flow flow = new Flow(
                groupId,
                request.locationId(),
                request.name(),
                request.description(),
                FlowStatus.ACTIVE
        );
        validate(flow);
        return FlowResponse.create(flowRepository.save(flow));
    }

    private void validate(Flow flow) {
        boolean nameExit = flowRepository.existsByGroupIdAndLocationIdAndName(flow.getGroupId(), flow.getLocationId(),
                flow.getName());
        if (nameExit) {
            throw new DuplicateFlowNameException(flow.getGroupId(), flow.getLocationId(), flow.getName());
        }
    }
}
