package com.nhnacademy.insightonruleengine.flow.application.cleanup;

import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.NodeRepository;
import com.nhnacademy.insightonruleengine.runner.application.schedule.ScheduleFlowScheduler;
import com.nhnacademy.insightonruleengine.runner.infrastructure.cache.ActiveFlowDefinitionProvider;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.EventGateStateRedisRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FlowCleanupService {

    private final FlowRepository flowRepository;
    private final NodeRepository nodeRepository;
    private final ActiveFlowDefinitionProvider activeFlowDefinitionProvider;
    private final EventGateStateRedisRepository eventGateStateRedisRepository;
    private final FlowCleanupDBService databaseCleanupService;
    private final ScheduleFlowScheduler scheduleFlowScheduler;

    public void cleanupByGroup(Long groupId, List<Long> locationIds) {
        if (groupId == null || groupId <= 0L) {
            throw new IllegalArgumentException("groupId는 양수여야 합니다.");
        }
        if (locationIds == null
                || locationIds.stream().anyMatch(locationId -> locationId == null || locationId <= 0L)) {
            throw new IllegalArgumentException("locationIds에는 양수만 사용할 수 있습니다.");
        }

        List<Flow> flows = flowRepository.findAllByGroupId(groupId).stream()
                .sorted(Comparator.comparing(Flow::getId))
                .toList();
        List<Long> flowIds = flows.stream().map(Flow::getId).toList();
        List<Node> nodes = flowIds.isEmpty() ? List.of() : nodeRepository.findByFlowIdIn(flowIds);
        Set<RouteKey> eventRouteKeys = new HashSet<>();
        locationIds.forEach(locationId -> eventRouteKeys.add(new RouteKey(groupId, locationId)));
        scheduleFlowScheduler.cancelAll(flowIds);
        cleanupRuntime(flows, nodes, eventRouteKeys);
        databaseCleanupService.deleteByGroupId(groupId);
        cleanupRuntime(flows, nodes, eventRouteKeys);
    }

    public void cleanupByLocation(Long locationId) {
        if (locationId == null || locationId <= 0L) {
            throw new IllegalArgumentException("locationId는 양수여야 합니다.");
        }

        List<Flow> flows = flowRepository.findAllByLocationId(locationId).stream()
                .sorted(Comparator.comparing(Flow::getId))
                .toList();
        List<Long> flowIds = flows.stream().map(Flow::getId).toList();
        List<Node> nodes = flowIds.isEmpty() ? List.of() : nodeRepository.findByFlowIdIn(flowIds);
        scheduleFlowScheduler.cancelAll(flowIds);
        cleanupRuntime(flows, nodes, Set.of());
        databaseCleanupService.deleteByLocationId(locationId);
        cleanupRuntime(flows, nodes, Set.of());
    }

    private void cleanupRuntime(List<Flow> flows, List<Node> nodes, Set<RouteKey> additionalRouteKeys) {
        Map<Long, Set<Long>> eventGateNodeIds = collectEventGateNodeIds(nodes);
        Set<RouteKey> routeKeys = new HashSet<>(additionalRouteKeys);
        for (Flow flow : flows) {
            routeKeys.add(new RouteKey(flow.getGroupId(), flow.getLocationId()));
        }
        routeKeys.forEach(routeKey -> activeFlowDefinitionProvider.evictNow(routeKey.groupId(), routeKey.locationId()));

        for (Flow flow : flows) {
            eventGateStateRedisRepository.deleteStates(
                    flow.getId(),
                    eventGateNodeIds.getOrDefault(flow.getId(), Set.of())
            );
        }
    }

    private Map<Long, Set<Long>> collectEventGateNodeIds(List<Node> nodes) {
        Map<Long, Set<Long>> mutableTargets = new HashMap<>();
        for (Node node : nodes) {
            if (node.getNodeType() == NodeType.EVENT_GATE) {
                mutableTargets.computeIfAbsent(node.getFlowId(), ignored -> new HashSet<>())
                        .add(node.getId());
            }
        }

        Map<Long, Set<Long>> targets = new HashMap<>();
        mutableTargets.forEach((flowId, nodeIds) -> targets.put(flowId, Set.copyOf(nodeIds)));
        return Map.copyOf(targets);
    }

    private record RouteKey(Long groupId, Long locationId) {
    }
}
