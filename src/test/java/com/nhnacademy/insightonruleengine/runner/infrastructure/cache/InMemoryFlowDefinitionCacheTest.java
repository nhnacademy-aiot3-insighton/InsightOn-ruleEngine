package com.nhnacademy.insightonruleengine.runner.infrastructure.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.runner.infrastructure.cache.InMemoryFlowDefinitionCache;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryFlowDefinitionCacheTest {

    private final InMemoryFlowDefinitionCache cache = new InMemoryFlowDefinitionCache();

    @Test
    void replacesAndFindsRouteSnapshot() {
        FlowDefinition definition = definition();

        cache.replace(1L, 10L, List.of(definition));

        assertEquals(List.of(definition), cache.find(1L, 10L).orElseThrow());
    }

    @Test
    void evictsRouteSnapshot() {
        cache.replace(1L, 10L, List.of(definition()));

        cache.evict(1L, 10L);

        assertTrue(cache.find(1L, 10L).isEmpty());
    }

    private FlowDefinition definition() {
        return new FlowDefinition(
                100L,
                1L,
                10L,
                "in-memory flow",
                null,
                FlowStatus.ACTIVE,
                OffsetDateTime.now(),
                List.of(new NodeDefinition(1L, NodeType.LOCATION, JsonNodeFactory.instance.objectNode())),
                List.of());
    }
}
