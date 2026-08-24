package com.nhnacademy.insightonruleengine.runner.recovery;

import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.exception.FlowNotActiveException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowNotFoundException;
import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import com.nhnacademy.insightonruleengine.runner.redis.ActiveFlowRedisRepository;
import com.nhnacademy.insightonruleengine.runner.redis.FlowRouteRedisRepository;
import com.nhnacademy.insightonruleengine.runner.redis.InvalidActiveFlowDataException;
import com.nhnacademy.insightonruleengine.runner.redis.InvalidRouteDataException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

//Redis MISS나 손상 데이터를 PostgreSQL의 ACTIVE Flow 원본으로 복구합니다.
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlowRuntimeRecoveryService {

    private final FlowRepository flowRepository;
    private final FlowDefinitionAssembler flowDefinitionAssembler;
    private final FlowRouteRedisRepository flowRouteRedisRepository;
    private final ActiveFlowRedisRepository activeFlowRedisRepository;

    public List<FlowDefinition> findActiveFlows(Long groupId, Long locationId) {
        Set<Long> flowIds;
        try {
            if (!flowRouteRedisRepository.exists(groupId, locationId)) {
                log.info("Redis Route MISS. groupId={}, locationId={}", groupId, locationId);
                return rebuildLocation(groupId, locationId);
            }
            flowIds = flowRouteRedisRepository.findFlowIds(groupId, locationId);
        } catch (InvalidRouteDataException exception) {
            log.warn("검증되지 않은 Flow Data입니다. groupId={}, locationId={}", groupId, locationId, exception);
            return rebuildLocation(groupId, locationId);
        } catch (RuntimeException exception) {
            log.error("Active Flow를 찾지 못했습니다. groupId={}, locationId={}", groupId, locationId, exception);
            throw exception;
        }
        List<FlowDefinition> definitions = new ArrayList<>();
        try {
            for (Long flowId : flowIds) {
                definitions.add(findOrRecoverDefinition(groupId, flowId));
            }
            return List.copyOf(definitions);
        } catch (FlowNotFoundException | FlowNotActiveException exception) {
            return rebuildLocation(groupId, locationId);
        }
    }

    public List<FlowDefinition> rebuildLocation(Long groupId, Long locationId) {
        List<Flow> activeFlows = flowRepository.findAllByGroupIdAndLocationIdAndStatus(groupId, locationId,
                FlowStatus.ACTIVE);
        List<FlowDefinition> definitions = activeFlows.stream()
                .map(flow -> flowDefinitionAssembler.assembleActive(groupId, flow.getId()))
                .toList();
        try {
            definitions.forEach(activeFlowRedisRepository::save);
            Set<Long> flowIds = definitions.stream()
                    .map(FlowDefinition::flowId)
                    .collect(Collectors.toUnmodifiableSet());
            flowRouteRedisRepository.replace(groupId, locationId, flowIds);
            return definitions;
        } catch (RuntimeException exception) {
            log.error("Flow Runtime 재구축 실패했습니다. groupId={}, locationId={}", groupId, locationId, exception);
            throw exception;
        }
    }

    public int rebuildAll() {
        Set<RouteKey> allLocations = flowRepository.findAll().stream()
                .map(flow -> new RouteKey(flow.getGroupId(), flow.getLocationId()))
                .collect(Collectors.toUnmodifiableSet());
        return allLocations.stream()
                .mapToInt(location -> rebuildLocation(location.groupId(), location.locationId()).size())
                .sum();
    }

    private FlowDefinition findOrRecoverDefinition(Long groupId, Long flowId) {
        Optional<FlowDefinition> storedDefinition;
        try {
            storedDefinition = activeFlowRedisRepository.getActiveFlow(groupId, flowId);
        } catch (InvalidActiveFlowDataException exception) {
            log.warn("검증되지 않은 Flow Data입니다. groupId={}, flowId={}", groupId, flowId, exception);
            storedDefinition = Optional.empty();
        } catch (RuntimeException exception) {
            log.error("Active Flow를 찾지 못했습니다. groupId={}, flowId={}", groupId, flowId, exception);
            throw exception;
        }
        return storedDefinition.orElseGet(() -> {
            FlowDefinition recoveredDefinition = flowDefinitionAssembler.assembleActive(groupId, flowId);
            activeFlowRedisRepository.save(recoveredDefinition);
            return recoveredDefinition;
        });
    }

    //라우팅 키 groupId + locationId
    private record RouteKey(Long groupId, Long locationId) {
    }
}
