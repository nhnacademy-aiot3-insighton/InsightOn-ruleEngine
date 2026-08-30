package com.nhnacademy.insightonruleengine.flow.application.cleanup;

import com.fasterxml.jackson.databind.JsonNode;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.AlertParams;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.NodeRepository;
import com.nhnacademy.insightonruleengine.runner.infrastructure.cache.ActiveFlowDefinitionProvider;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.AlertCountRedisRepository;
import com.nhnacademy.insightonruleengine.runner.application.schedule.ScheduleFlowScheduler;
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
    private final AlertCountRedisRepository alertCountRedisRepository;
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
        Map<Long, RuntimeStateNodeIds> runtimeStateNodeIds = collectRuntimeStateNodeIds(nodes);
        Set<RouteKey> routeKeys = new HashSet<>(additionalRouteKeys);
        for (Flow flow : flows) {
            routeKeys.add(new RouteKey(flow.getGroupId(), flow.getLocationId()));
        }
        routeKeys.forEach(routeKey -> {
            activeFlowDefinitionProvider.evictNow(routeKey.groupId(), routeKey.locationId());
        });

        for (Flow flow : flows) {
            RuntimeStateNodeIds nodeIds = runtimeStateNodeIds.getOrDefault(
                    flow.getId(),
                    RuntimeStateNodeIds.empty()
            );
            alertCountRedisRepository.deleteStates(
                    flow.getId(),
                    nodeIds.countNodeIds(),
                    nodeIds.cooldownNodeIds()
            );
        }
    }

    private Map<Long, RuntimeStateNodeIds> collectRuntimeStateNodeIds(List<Node> nodes) {
        Map<Long, MutableRuntimeStateNodeIds> mutableTargets = new HashMap<>();
        for (Node node : nodes) {
            if (node.getNodeType() != NodeType.ALERT) {
                continue;
            }
            JsonNode configuration = node.getConfiguration();
            int requiredCount = configuration == null
                    ? AlertParams.DEFAULT_REQUIRED_COUNT
                    : configuration.path("requiredCount").asInt(AlertParams.DEFAULT_REQUIRED_COUNT);
            int cooldownSeconds = configuration == null
                    ? AlertParams.DEFAULT_COOLDOWN_SECONDS
                    : configuration.path("cooldownSeconds").asInt(AlertParams.DEFAULT_COOLDOWN_SECONDS);
            MutableRuntimeStateNodeIds target = mutableTargets.computeIfAbsent(
                    node.getFlowId(),
                    ignored -> new MutableRuntimeStateNodeIds()
            );
            if (requiredCount > 1) {
                target.countNodeIds.add(node.getId());
            }
            if (cooldownSeconds > 0) {
                target.cooldownNodeIds.add(node.getId());
            }
        }

        Map<Long, RuntimeStateNodeIds> targets = new HashMap<>();
        mutableTargets.forEach((flowId, nodeIds) -> targets.put(
                flowId,
                new RuntimeStateNodeIds(
                        Set.copyOf(nodeIds.countNodeIds),
                        Set.copyOf(nodeIds.cooldownNodeIds)
                )
        ));
        return Map.copyOf(targets);
    }

    private static final class MutableRuntimeStateNodeIds {
        private final Set<Long> countNodeIds = new HashSet<>();
        private final Set<Long> cooldownNodeIds = new HashSet<>();
    }

    private record RuntimeStateNodeIds(Set<Long> countNodeIds, Set<Long> cooldownNodeIds) {
        private static RuntimeStateNodeIds empty() {
            return new RuntimeStateNodeIds(Set.of(), Set.of());
        }
    }

    private record RouteKey(Long groupId, Long locationId) {
    }
}
