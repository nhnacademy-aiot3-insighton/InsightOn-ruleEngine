package com.nhnacademy.insightonruleengine.runner.lifecycle;

import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.event.FlowRuntimeChangeEvent;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import com.nhnacademy.insightonruleengine.runner.alert.AlertCountRedisRepository;
import com.nhnacademy.insightonruleengine.runner.redis.ActiveFlowRedisRepository;
import com.nhnacademy.insightonruleengine.runner.redis.FlowRouteRedisRepository;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

//DB에 확정된 Flow 상태를 Redis 실행 정보에 반영합니다.
@Component
@RequiredArgsConstructor
public class FlowRuntimeSynchronizer {

    private final FlowRepository flowRepository;
    private final FlowDefinitionAssembler flowDefinitionAssembler;
    private final FlowRouteRedisRepository flowRouteRedisRepository;
    private final ActiveFlowRedisRepository activeFlowRedisRepository;
    private final AlertCountRedisRepository alertCountRedisRepository;

    public void activate(FlowRuntimeChangeEvent event) {
        FlowDefinition definition = flowDefinitionAssembler.assemble(event.groupId(), event.flowId());
        activeFlowRedisRepository.save(definition);
        refreshRoute(event.groupId(), event.locationId());
    }

    public void remove(FlowRuntimeChangeEvent event) {
        RuntimeException failure = null;
        failure = runCleanup(
                () -> refreshRoute(event.groupId(), event.locationId()),
                failure
        );
        failure = runCleanup(
                () -> activeFlowRedisRepository.delete(event.groupId(), event.flowId()),
                failure
        );
        failure = runCleanup(
                () -> alertCountRedisRepository.deleteStates(event.flowId(), event.runtimeNodeIds()),
                failure
        );
        if(failure != null) {
            throw failure;
        }
    }

    public void refreshRoute(Long groupId, Long locationId) {
        Set<Long> activeFlowIds = flowRepository.findAllByGroupIdAndLocationIdAndStatus(
                        groupId,
                        locationId,
                        FlowStatus.ACTIVE
                ).stream()
                .map(flow -> flow.getId())
                .collect(Collectors.toUnmodifiableSet());
        flowRouteRedisRepository.replace(groupId, locationId, activeFlowIds);
    }

    private RuntimeException runCleanup(Runnable cleanup, RuntimeException exception) {
        try {
            cleanup.run();
            return exception;
        } catch (RuntimeException e) {
            if (exception == null) {
                return e;
            }
            exception.addSuppressed(e);
            return exception;
        }
    }
}
