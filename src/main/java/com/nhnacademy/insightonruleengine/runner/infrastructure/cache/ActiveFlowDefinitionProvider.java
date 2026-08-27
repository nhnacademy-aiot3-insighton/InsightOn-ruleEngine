package com.nhnacademy.insightonruleengine.runner.infrastructure.cache;

import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.application.assembly.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final AtomicBoolean redisDegraded = new AtomicBoolean();
    private final AtomicBoolean localFallbackActive = new AtomicBoolean();

    public List<FlowDefinition> find(Long groupId, Long locationId) {
        RouteKey routeKey = new RouteKey(groupId, locationId);
        Optional<List<FlowDefinition>> cachedDefinitions;
        try {
            cachedDefinitions = flowDefinitionCache.find(groupId, locationId);
            markRedisRecovered();
        } catch (RuntimeException cacheException) {
            logRedisFailure("조회", routeKey, cacheException);
            return rebuildOrFallback(routeKey, cacheException);
        }

        if (cachedDefinitions.isPresent()) {
            markLocalFallbackRecovered();
            return remember(routeKey, cachedDefinitions.get());
        }
        try {
            List<FlowDefinition> definitions = rebuild(routeKey);
            markLocalFallbackRecovered();
            return definitions;
        } catch (RuntimeException databaseException) {
            return localFallbackOrThrow(routeKey, databaseException);
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
        log.info("활성 플로우 캐시 적재를 완료했습니다. routeCount={}", grouped.size());
    }

    public void refreshAfterCommit(Long groupId, Long locationId) {
        afterCommit(() -> replaceNow(new RouteKey(groupId, locationId)));
    }

    public void evictAfterCommit(Long groupId, Long locationId) {
        afterCommit(() -> {
            try {
                evictNow(groupId, locationId);
            } catch (RuntimeException exception) {
                logRedisFailure("삭제", new RouteKey(groupId, locationId), exception);
            }
        });
    }

    public void evictNow(Long groupId, Long locationId) {
        localFallback.remove(new RouteKey(groupId, locationId));
        flowDefinitionCache.evict(groupId, locationId);
        markRecovered();
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
            markRecovered();
        } catch (RuntimeException exception) {
            logRedisFailure("저장", routeKey, exception);
        }
    }

    private List<FlowDefinition> remember(RouteKey routeKey, List<FlowDefinition> definitions) {
        List<FlowDefinition> snapshot = List.copyOf(definitions);
        localFallback.put(routeKey, snapshot);
        return snapshot;
    }

    private List<FlowDefinition> rebuildOrFallback(
            RouteKey routeKey,
            RuntimeException cacheException
    ) {
        try {
            List<FlowDefinition> definitions = rebuild(routeKey);
            markLocalFallbackRecovered();
            return definitions;
        } catch (RuntimeException databaseException) {
            databaseException.addSuppressed(cacheException);
            return localFallbackOrThrow(routeKey, databaseException);
        }
    }

    private List<FlowDefinition> localFallbackOrThrow(
            RouteKey routeKey,
            RuntimeException exception
    ) {
        List<FlowDefinition> fallback = localFallback.get(routeKey);
        if (fallback == null) {
            throw exception;
        }
        logLocalFallback(routeKey, exception);
        return fallback;
    }

    private void logRedisFailure(String operation, RouteKey routeKey, RuntimeException exception) {
        if (redisDegraded.compareAndSet(false, true)) {
            log.warn(
                    "Redis 플로우 캐시 {}에 실패해 대체 경로를 사용합니다. "
                            + "groupId={}, locationId={}, errorType={}, message={}",
                    operation,
                    routeKey.groupId(),
                    routeKey.locationId(),
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            log.debug("Redis 플로우 캐시 장애 상세.", exception);
        }
    }

    private void logLocalFallback(RouteKey routeKey, RuntimeException exception) {
        if (localFallbackActive.compareAndSet(false, true)) {
            log.warn(
                    "Redis 캐시 또는 DB 원본을 사용할 수 없어 로컬 플로우 캐시를 사용합니다. "
                            + "groupId={}, locationId={}, errorType={}, message={}",
                    routeKey.groupId(),
                    routeKey.locationId(),
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            log.debug("로컬 플로우 캐시 사용 상세.", exception);
        }
    }

    private void markRecovered() {
        markRedisRecovered();
        markLocalFallbackRecovered();
    }

    private void markRedisRecovered() {
        if (redisDegraded.compareAndSet(true, false)) {
            log.info("Redis 플로우 캐시 연결이 복구됐습니다.");
        }
    }

    private void markLocalFallbackRecovered() {
        if (localFallbackActive.compareAndSet(true, false)) {
            log.info("플로우 라우팅의 Redis 또는 DB 조회가 복구됐습니다.");
        }
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
