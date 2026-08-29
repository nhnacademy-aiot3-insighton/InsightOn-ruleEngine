package com.nhnacademy.insightonruleengine.runner.infrastructure.cache;

import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.application.assembly.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    static final int MAX_TRACKED_LOCAL_FALLBACK_FAILURES = 10_000;

    private final FlowRepository flowRepository;
    private final FlowDefinitionAssembler flowDefinitionAssembler;
    private final FlowDefinitionCache flowDefinitionCache;
    private final Map<RouteKey, List<FlowDefinition>> localFallback = new ConcurrentHashMap<>();
    private final Map<RouteKey, LocalFallbackFailureState> localFallbackFailures =
            new LinkedHashMap<>(16, 0.75f, true);
    private final AtomicBoolean flowCacheDegraded = new AtomicBoolean();

    public List<FlowDefinition> find(Long groupId, Long locationId) {
        RouteKey routeKey = new RouteKey(groupId, locationId);
        Optional<List<FlowDefinition>> cachedDefinitions;
        try {
            cachedDefinitions = flowDefinitionCache.find(groupId, locationId);
            markFlowCacheRecovered();
        } catch (RuntimeException cacheException) {
            logFlowCacheFailure("조회", routeKey, cacheException);
            return rebuildOrFallback(routeKey, cacheException);
        }

        if (cachedDefinitions.isPresent()) {
            markLocalFallbackRecovered(routeKey);
            return remember(routeKey, cachedDefinitions.get());
        }
        try {
            List<FlowDefinition> definitions = rebuild(routeKey);
            markLocalFallbackRecovered(routeKey);
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
                logFlowCacheFailure("삭제", new RouteKey(groupId, locationId), exception);
            }
        });
    }

    public void evictNow(Long groupId, Long locationId) {
        RouteKey routeKey = new RouteKey(groupId, locationId);
        localFallback.remove(routeKey);
        flowDefinitionCache.evict(groupId, locationId);
        removeLocalFallbackFailure(routeKey);
        markFlowCacheRecovered();
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
            markRecovered(routeKey);
        } catch (RuntimeException exception) {
            logFlowCacheFailure("저장", routeKey, exception);
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
            markLocalFallbackRecovered(routeKey);
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

    private void logFlowCacheFailure(String operation, RouteKey routeKey, RuntimeException exception) {
        if (flowCacheDegraded.compareAndSet(false, true)) {
            log.warn(
                    "플로우 캐시 {}에 실패해 대체 경로를 사용합니다. "
                            + "groupId={}, locationId={}, errorType={}, message={}",
                    operation,
                    routeKey.groupId(),
                    routeKey.locationId(),
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            log.debug("플로우 캐시 장애 상세.", exception);
        }
    }

    private void logLocalFallback(RouteKey routeKey, RuntimeException exception) {
        LocalFallbackFailureState failure;
        synchronized (localFallbackFailures) {
            makeRoomForLocalFallbackFailure(routeKey);
            failure = localFallbackFailures.compute(routeKey, (ignored, current) -> {
                if (current == null) {
                    return LocalFallbackFailureState.first(exception);
                }
                return current.incremented(exception);
            });
        }
        if (failure.suppressedFailureCount() == 0L) {
            log.warn(
                    "플로우 캐시 또는 DB 원본을 사용할 수 없어 로컬 플로우 캐시를 사용합니다. "
                            + "groupId={}, locationId={}, errorType={}, message={}",
                    routeKey.groupId(),
                    routeKey.locationId(),
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            log.debug("로컬 플로우 캐시 사용 상세.", exception);
        }
    }

    int trackedLocalFallbackFailureCount() {
        synchronized (localFallbackFailures) {
            return localFallbackFailures.size();
        }
    }

    private void makeRoomForLocalFallbackFailure(RouteKey routeKey) {
        if (localFallbackFailures.containsKey(routeKey)) {
            return;
        }
        while (localFallbackFailures.size() >= MAX_TRACKED_LOCAL_FALLBACK_FAILURES) {
            RouteKey evictionCandidate = localFallbackFailures.keySet().iterator().next();
            // access-order LinkedHashMap의 가장 오래 사용하지 않은 라우트 상태부터 축출한다.
            localFallbackFailures.remove(evictionCandidate);
        }
    }

    private void markRecovered(RouteKey routeKey) {
        markFlowCacheRecovered();
        markLocalFallbackRecovered(routeKey);
    }

    private void markFlowCacheRecovered() {
        if (flowCacheDegraded.compareAndSet(true, false)) {
            log.info("플로우 캐시 접근이 정상화됐습니다.");
        }
    }

    private void markLocalFallbackRecovered(RouteKey routeKey) {
        LocalFallbackFailureState recovered = removeLocalFallbackFailure(routeKey);
        if (recovered != null) {
            log.info("플로우 라우팅의 캐시 또는 DB 조회가 정상화됐습니다. "
                            + "groupId={}, locationId={}, lastErrorType={}, lastMessage={}, "
                            + "suppressedFailureCount={}",
                    routeKey.groupId(),
                    routeKey.locationId(),
                    recovered.lastErrorType(),
                    recovered.lastMessage(),
                    recovered.suppressedFailureCount());
        }
    }

    private LocalFallbackFailureState removeLocalFallbackFailure(RouteKey routeKey) {
        synchronized (localFallbackFailures) {
            return localFallbackFailures.remove(routeKey);
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

    private record LocalFallbackFailureState(
            String lastErrorType,
            String lastMessage,
            long suppressedFailureCount
    ) {

        private static LocalFallbackFailureState first(RuntimeException exception) {
            return new LocalFallbackFailureState(
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    0L
            );
        }

        private LocalFallbackFailureState incremented(RuntimeException exception) {
            return new LocalFallbackFailureState(
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    suppressedFailureCount + 1L
            );
        }
    }
}
