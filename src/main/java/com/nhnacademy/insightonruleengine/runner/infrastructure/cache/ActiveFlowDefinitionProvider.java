package com.nhnacademy.insightonruleengine.runner.cache;

import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.infrastructure.FlowRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 이벤트 실행 경로에서 ACTIVE Flow를 제공한다.
 * 정상 경로는 Redis이며, Redis 장애 시 DB 원본을 먼저 확인하고 DB도 실패할 때만
 * 같은 인스턴스의 최근 값을 제한적으로 사용한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActiveFlowDefinitionProvider {

    private final FlowRepository flowRepository;
    private final FlowDefinitionAssembler flowDefinitionAssembler;
    private final FlowDefinitionCache flowDefinitionCache;
    private final Map<RouteKey, List<FlowDefinition>> localFallback = new ConcurrentHashMap<>();

    public List<FlowDefinition> find(Long groupId, Long locationId) {
        RouteKey routeKey = new RouteKey(groupId, locationId);
        try {
            return flowDefinitionCache.find(groupId, locationId)
                    .map(definitions -> remember(routeKey, definitions))
                    .orElseGet(() -> rebuild(routeKey));
        } catch (RuntimeException cacheException) {
            log.warn("Flow 캐시 조회에 실패하여 DB 원본에서 다시 확인합니다. groupId={}, locationId={}",
                    groupId, locationId, cacheException);
            try {
                return rebuild(routeKey);
            } catch (RuntimeException databaseException) {
                List<FlowDefinition> fallback = localFallback.get(routeKey);
                if (fallback != null) {
                    log.warn("Redis와 DB 조회가 모두 실패하여 로컬 캐시를 사용합니다. groupId={}, locationId={}",
                            groupId, locationId, databaseException);
                    return fallback;
                }
                databaseException.addSuppressed(cacheException);
                throw databaseException;
            }
        }
    }

    /**
     * 애플리케이션이 준비된 뒤 ACTIVE Flow를 Redis에 적재한다.
     * Redis가 일시적으로 unavailable이어도 로컬 fallback은 채운다.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void warmUp() {
        Map<RouteKey, List<FlowDefinition>> grouped = new ConcurrentHashMap<>();
        for (Flow flow : flowRepository.findAllByStatus(FlowStatus.ACTIVE)) {
            RouteKey key = new RouteKey(flow.getGroupId(), flow.getLocationId());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(flowDefinitionAssembler.assemble(flow.getGroupId(), flow.getId()));
        }

        grouped.forEach(this::replaceNow);
        log.info("ACTIVE Flow 캐시 적재를 완료했습니다. routeCount={}", grouped.size());
    }

    public void refreshAfterCommit(Long groupId, Long locationId) {
        afterCommit(() -> replaceNow(new RouteKey(groupId, locationId)));
    }

    public void evictAfterCommit(Long groupId, Long locationId) {
        afterCommit(() -> {
            try {
                evictNow(groupId, locationId);
            } catch (RuntimeException exception) {
                log.warn("Flow 캐시 삭제에 실패했습니다. groupId={}, locationId={}", groupId, locationId, exception);
            }
        });
    }

    public void evictNow(Long groupId, Long locationId) {
        localFallback.remove(new RouteKey(groupId, locationId));
        flowDefinitionCache.evict(groupId, locationId);
    }

    private List<FlowDefinition> rebuild(RouteKey routeKey) {
        List<FlowDefinition> definitions = flowRepository
                .findAllByGroupIdAndLocationIdAndStatus(
                        routeKey.groupId(), routeKey.locationId(), FlowStatus.ACTIVE)
                .stream()
                .map(flow -> flowDefinitionAssembler.assemble(flow.getGroupId(), flow.getId()))
                .toList();
        replaceNow(routeKey, definitions);
        return definitions;
    }

    private void replaceNow(RouteKey routeKey) {
        replaceNow(routeKey, flowRepository
                .findAllByGroupIdAndLocationIdAndStatus(
                        routeKey.groupId(), routeKey.locationId(), FlowStatus.ACTIVE)
                .stream()
                .map(flow -> flowDefinitionAssembler.assemble(flow.getGroupId(), flow.getId()))
                .toList());
    }

    private void replaceNow(RouteKey routeKey, List<FlowDefinition> definitions) {
        List<FlowDefinition> snapshot = List.copyOf(definitions);
        localFallback.put(routeKey, snapshot);
        try {
            // Redis SET은 한 키에 대한 원자적 교체이므로 갱신 중 빈 라우팅 상태를 만들지 않는다.
            flowDefinitionCache.replace(routeKey.groupId(), routeKey.locationId(), snapshot);
        } catch (RuntimeException exception) {
            log.warn("Flow 캐시 저장에 실패했습니다. 로컬 fallback은 유지합니다. groupId={}, locationId={}",
                    routeKey.groupId(), routeKey.locationId(), exception);
        }
    }

    private List<FlowDefinition> remember(RouteKey routeKey, List<FlowDefinition> definitions) {
        List<FlowDefinition> snapshot = List.copyOf(definitions);
        localFallback.put(routeKey, snapshot);
        return snapshot;
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private record RouteKey(Long groupId, Long locationId) {
    }
}
