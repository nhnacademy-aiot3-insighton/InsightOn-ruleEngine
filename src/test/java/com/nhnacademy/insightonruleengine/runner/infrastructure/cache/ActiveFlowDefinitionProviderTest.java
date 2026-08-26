package com.nhnacademy.insightonruleengine.runner.infrastructure.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.application.assembly.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    @BeforeEach
    void setUp() {
        provider = new ActiveFlowDefinitionProvider(
                flowRepository,
                flowDefinitionAssembler,
                flowDefinitionCache);
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
    void redisFailureOnAnotherInstanceRechecksDatabaseBeforeUsingLocalFallback() {
        FlowDefinition definition = definition();
        when(flowDefinitionCache.find(1L, 10L))
                .thenReturn(Optional.of(List.of(definition)))
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(1L, 10L, FlowStatus.ACTIVE))
                .thenReturn(List.of());

        assertEquals(List.of(definition), provider.find(1L, 10L));
        assertEquals(List.of(), provider.find(1L, 10L));

        verify(flowDefinitionCache).replace(1L, 10L, List.of());
    }

    @Test
    void usesLocalFallbackOnlyWhenRedisAndDatabaseBothFail() {
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
        assertEquals(List.of(definition), provider.find(1L, 10L));
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
