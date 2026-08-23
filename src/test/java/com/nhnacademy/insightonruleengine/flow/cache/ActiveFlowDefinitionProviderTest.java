package com.nhnacademy.insightonruleengine.flow.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.runner.cache.ActiveFlowDefinitionProvider;
import com.nhnacademy.insightonruleengine.runner.cache.FlowDefinitionCache;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.infrastructure.FlowRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

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
        Flow flow =
                org.mockito.Mockito.mock(Flow.class);
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

    private FlowDefinition definition() {
        return new FlowDefinition(
                100L,
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
