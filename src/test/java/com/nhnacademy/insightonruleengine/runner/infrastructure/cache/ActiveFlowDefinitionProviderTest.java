package com.nhnacademy.insightonruleengine.runner.infrastructure.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.application.assembly.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ActiveFlowDefinitionProviderTest {

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private FlowDefinitionAssembler flowDefinitionAssembler;

    @Mock
    private FlowDefinitionCache flowDefinitionCache;

    private ActiveFlowDefinitionProvider provider;
    private AtomicLong nanoTime;

    @BeforeEach
    void setUp() {
        nanoTime = new AtomicLong();
        provider = new ActiveFlowDefinitionProvider(
                flowRepository,
                flowDefinitionAssembler,
                flowDefinitionCache,
                nanoTime::get);
    }

    @Test
    void cacheHitDoesNotReadDatabase() {
        FlowDefinition definition = definition();
        when(flowDefinitionCache.find(1L, 10L)).thenReturn(Optional.of(List.of(definition)));

        assertEquals(List.of(definition), provider.find(1L, 10L));

        verifyNoInteractions(flowRepository, flowDefinitionAssembler);
    }

    @Test
    void cacheMissRebuildsDefinitionFromDatabase() {
        FlowDefinition definition = definition();
        Flow flow = mock(Flow.class);
        when(flow.getGroupId()).thenReturn(1L);
        when(flow.getId()).thenReturn(100L);
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(1L, 10L, FlowStatus.ACTIVE))
                .thenReturn(List.of(flow));
        when(flowDefinitionCache.find(1L, 10L)).thenReturn(Optional.empty());
        when(flowDefinitionAssembler.assemble(1L, 100L)).thenReturn(definition);

        assertEquals(List.of(definition), provider.find(1L, 10L));

        verify(flowDefinitionCache).replace(1L, 10L, List.of(definition));
    }

    @Test
    void evictNowRemovesRedisCacheAndLocalFallback() {
        FlowDefinition definition = definition();
        when(flowDefinitionCache.find(1L, 10L))
                .thenReturn(Optional.of(List.of(definition)))
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(1L, 10L, FlowStatus.ACTIVE))
                .thenReturn(List.of());

        assertEquals(List.of(definition), provider.find(1L, 10L));
        provider.evictNow(1L, 10L);
        assertEquals(List.of(), provider.find(1L, 10L));

        verify(flowDefinitionCache).evict(1L, 10L);
    }

    @Test
    void redisFailureUsesRecentLocalSnapshotWithoutReadingDatabase() {
        FlowDefinition definition = definition();
        when(flowDefinitionCache.find(1L, 10L))
                .thenReturn(Optional.of(List.of(definition)))
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));

        assertEquals(List.of(definition), provider.find(1L, 10L));
        assertEquals(List.of(definition), provider.find(1L, 10L));
        assertEquals(List.of(definition), provider.find(1L, 10L));

        verifyNoInteractions(flowRepository, flowDefinitionAssembler);
    }

    @Test
    void usesRecentLocalFallbackDuringCacheRetryDelay() {
        FlowDefinition definition = definition();
        RedisConnectionFailureException redisFailure =
                new RedisConnectionFailureException("Redis unavailable");
        when(flowDefinitionCache.find(1L, 10L))
                .thenReturn(Optional.of(List.of(definition)))
                .thenThrow(redisFailure);

        assertEquals(List.of(definition), provider.find(1L, 10L));
        assertEquals(List.of(definition), provider.find(1L, 10L));
        assertEquals(List.of(definition), provider.find(1L, 10L));

        verifyNoInteractions(flowRepository, flowDefinitionAssembler);
    }

    @Test
    void expiredLocalSnapshotRebuildsFromDatabaseAfterRemoteDeactivation() {
        Duration maxAge = Duration.ofSeconds(30);
        provider = new ActiveFlowDefinitionProvider(
                flowRepository,
                flowDefinitionAssembler,
                flowDefinitionCache,
                nanoTime::get,
                maxAge
        );
        FlowDefinition definition = definition();
        when(flowDefinitionCache.find(1L, 10L))
                .thenReturn(Optional.of(List.of(definition)))
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(1L, 10L, FlowStatus.ACTIVE))
                .thenReturn(List.of());

        assertEquals(List.of(definition), provider.find(1L, 10L));
        nanoTime.addAndGet(maxAge.toNanos() + 1L);

        assertEquals(List.of(), provider.find(1L, 10L));

        verify(flowRepository)
                .findAllByGroupIdAndLocationIdAndStatus(1L, 10L, FlowStatus.ACTIVE);
    }

    @Test
    void expiredLocalSnapshotIsNotUsedWhenRedisAndDatabaseAreUnavailable() {
        Duration maxAge = Duration.ofSeconds(30);
        provider = new ActiveFlowDefinitionProvider(
                flowRepository,
                flowDefinitionAssembler,
                flowDefinitionCache,
                nanoTime::get,
                maxAge
        );
        FlowDefinition definition = definition();
        RedisConnectionFailureException redisFailure =
                new RedisConnectionFailureException("Redis unavailable");
        IllegalStateException databaseFailure = new IllegalStateException("DB unavailable");
        when(flowDefinitionCache.find(1L, 10L))
                .thenReturn(Optional.of(List.of(definition)))
                .thenThrow(redisFailure);
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(1L, 10L, FlowStatus.ACTIVE))
                .thenThrow(databaseFailure);

        assertEquals(List.of(definition), provider.find(1L, 10L));
        nanoTime.addAndGet(maxAge.toNanos() + 1L);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> provider.find(1L, 10L)
        );

        assertSame(databaseFailure, thrown);
        assertEquals(List.of(redisFailure), List.of(thrown.getSuppressed()));
    }

    @Test
    void recoversLocalFallbackOnlyForTheRouteThatActuallyRecovered() {
        FlowDefinition definition = definition();
        RedisConnectionFailureException redisFailure =
                new RedisConnectionFailureException("Redis unavailable");
        when(flowDefinitionCache.find(1L, 10L))
                .thenReturn(Optional.of(List.of(definition)))
                .thenThrow(redisFailure)
                .thenThrow(redisFailure)
                .thenReturn(Optional.of(List.of(definition)));
        when(flowDefinitionCache.find(2L, 20L))
                .thenReturn(Optional.of(List.of(definition)));

        Logger logger = (Logger) LoggerFactory.getLogger(ActiveFlowDefinitionProvider.class);
        Level originalLevel = logger.getLevel();
        boolean originalAdditive = logger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        appender.start();
        logger.addAppender(appender);
        try {
            provider.find(1L, 10L);
            advancePastCacheRetryDelay();
            provider.find(1L, 10L);
            provider.find(2L, 20L);
            advancePastCacheRetryDelay();
            provider.find(1L, 10L);

            assertEquals(0L, appender.list.stream()
                    .filter(event -> event.getFormattedMessage()
                            .startsWith("플로우 라우팅의 캐시 또는 DB 조회가 정상화됐습니다."))
                    .count());

            advancePastCacheRetryDelay();
            provider.find(1L, 10L);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(originalLevel);
            logger.setAdditive(originalAdditive);
        }

        assertEquals(1L, appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .filter(event -> event.getFormattedMessage().startsWith(
                        "공유 실행 원본 대신 최근 로컬 플로우 캐시를 사용합니다."))
                .count());
        assertTrue(appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith(
                        "플로우 라우팅의 캐시 또는 DB 조회가 정상화됐습니다."))
                .anyMatch(message -> message.contains("groupId=1")
                        && message.contains("locationId=10")
                        && message.contains("lastErrorType=RedisConnectionFailureException")
                        && message.contains("lastMessage=Redis unavailable")
                        && message.contains("suppressedFailureCount=1")));
    }

    @Test
    void boundsLocalFallbackFailureStatesAtTenThousandRoutes() {
        AtomicInteger cacheReads = new AtomicInteger();
        FlowDefinition definition = definition();
        when(flowDefinitionCache.find(anyLong(), anyLong())).thenAnswer(invocation -> {
            if (cacheReads.getAndIncrement() % 2 == 0) {
                return Optional.of(List.of(definition));
            }
            throw new RedisConnectionFailureException("Redis unavailable");
        });
        Logger logger = (Logger) LoggerFactory.getLogger(ActiveFlowDefinitionProvider.class);
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.OFF);
        try {
            for (long routeId = 1L;
                 routeId <= ActiveFlowDefinitionProvider.MAX_TRACKED_LOCAL_FALLBACK_FAILURES + 1L;
                 routeId++) {
                provider.find(routeId, routeId);
                advancePastCacheRetryDelay();
                provider.find(routeId, routeId);
            }
        } finally {
            logger.setLevel(originalLevel);
        }

        assertEquals(
                ActiveFlowDefinitionProvider.MAX_TRACKED_LOCAL_FALLBACK_FAILURES,
                provider.trackedLocalFallbackFailureCount());
    }

    @Test
    void propagatesDatabaseFailureWhenNoLocalFallbackExists() {
        RedisConnectionFailureException redisFailure =
                new RedisConnectionFailureException("Redis unavailable");
        IllegalStateException databaseFailure = new IllegalStateException("DB unavailable");
        when(flowDefinitionCache.find(1L, 10L)).thenThrow(redisFailure);
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(1L, 10L, FlowStatus.ACTIVE))
                .thenThrow(databaseFailure);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> provider.find(1L, 10L)
        );

        assertSame(databaseFailure, thrown);
        assertEquals(List.of(redisFailure), List.of(thrown.getSuppressed()));
    }

    @Test
    void keepsLocalFallbackWhenRedisReplaceFails() {
        FlowDefinition definition = definition();
        Flow flow = flow(100L);
        RedisConnectionFailureException redisReadFailure =
                new RedisConnectionFailureException("Redis read unavailable");
        IllegalStateException databaseFailure = new IllegalStateException("DB unavailable");
        when(flowDefinitionCache.find(1L, 10L))
                .thenReturn(Optional.empty())
                .thenThrow(redisReadFailure);
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(1L, 10L, FlowStatus.ACTIVE))
                .thenReturn(List.of(flow))
                .thenThrow(databaseFailure);
        when(flowDefinitionAssembler.assemble(1L, 100L)).thenReturn(definition);
        doThrow(new RedisConnectionFailureException("Redis write unavailable"))
                .when(flowDefinitionCache).replace(1L, 10L, List.of(definition));

        assertEquals(List.of(definition), provider.find(1L, 10L));
        assertEquals(List.of(definition), provider.find(1L, 10L));
    }

    @Test
    void warmUpGroupsActiveFlowsByRoute() {
        FlowDefinition firstDefinition = definition();
        FlowDefinition secondDefinition = definition(101L);
        Flow firstFlow = flow(100L);
        Flow secondFlow = flow(101L);
        when(flowRepository.findAllByStatus(FlowStatus.ACTIVE))
                .thenReturn(List.of(firstFlow, secondFlow));
        when(flowDefinitionAssembler.assemble(1L, 100L)).thenReturn(firstDefinition);
        when(flowDefinitionAssembler.assemble(1L, 101L)).thenReturn(secondDefinition);

        provider.warmUp();

        verify(flowDefinitionCache)
                .replace(1L, 10L, List.of(firstDefinition, secondDefinition));
    }

    @Test
    void refreshRunsImmediatelyWithoutTransactionSynchronization() {
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(1L, 10L, FlowStatus.ACTIVE))
                .thenReturn(List.of());

        provider.refreshAfterCommit(1L, 10L);

        verify(flowDefinitionCache).replace(1L, 10L, List.of());
    }

    @Test
    void refreshWaitsForTransactionCommitWhenSynchronizationIsActive() {
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(1L, 10L, FlowStatus.ACTIVE))
                .thenReturn(List.of());
        TransactionSynchronizationManager.initSynchronization();
        try {
            provider.refreshAfterCommit(1L, 10L);
            verifyNoInteractions(flowRepository, flowDefinitionAssembler, flowDefinitionCache);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(flowDefinitionCache).replace(1L, 10L, List.of());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void evictAfterCommitDoesNotPropagateRedisFailure() {
        doThrow(new RedisConnectionFailureException("Redis unavailable"))
                .when(flowDefinitionCache).evict(1L, 10L);

        provider.evictAfterCommit(1L, 10L);

        verify(flowDefinitionCache).evict(1L, 10L);
    }

    private Flow flow(long flowId) {
        Flow flow = mock(Flow.class);
        when(flow.getGroupId()).thenReturn(1L);
        lenient().when(flow.getLocationId()).thenReturn(10L);
        when(flow.getId()).thenReturn(flowId);
        return flow;
    }

    private void advancePastCacheRetryDelay() {
        nanoTime.addAndGet(ActiveFlowDefinitionProvider.CACHE_RETRY_DELAY_NANOS + 1L);
    }

    private FlowDefinition definition() {
        return definition(100L);
    }

    private FlowDefinition definition(long flowId) {
        return new FlowDefinition(
                flowId,
                1L,
                10L,
                "cached flow",
                null,
                FlowStatus.ACTIVE,
                OffsetDateTime.now(),
                List.of(new NodeDefinition(1L, NodeType.LOCATION, JsonNodeFactory.instance.objectNode())),
                List.of());
    }
}
