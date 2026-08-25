package com.nhnacademy.insightonruleengine.runner.infrastructure.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisFlowDefinitionCacheTest {

    private static final String KEY = "rule-engine:flow-route:1:10";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private RedisFlowDefinitionCache cache;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        cache = new RedisFlowDefinitionCache(redisTemplate, objectMapper);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void returnsEmptyWhenRouteDoesNotExist() {
        when(valueOperations.get(KEY)).thenReturn(null);

        assertEquals(Optional.empty(), cache.find(1L, 10L));
    }

    @Test
    void readsStoredFlowDefinitions() throws Exception {
        FlowDefinition definition = definition();
        when(valueOperations.get(KEY)).thenReturn(objectMapper.writeValueAsString(List.of(definition)));

        assertEquals(Optional.of(List.of(definition)), cache.find(1L, 10L));
    }

    @Test
    void replacesAndEvictsRouteSnapshot() {
        FlowDefinition definition = definition();

        cache.replace(1L, 10L, List.of(definition));
        cache.evict(1L, 10L);

        verify(valueOperations).set(org.mockito.ArgumentMatchers.eq(KEY), anyString());
        verify(redisTemplate).delete(KEY);
    }

    @Test
    void rejectsCorruptedStoredValue() {
        when(valueOperations.get(KEY)).thenReturn("not-json");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> cache.find(1L, 10L)
        );

        assertTrue(exception.getMessage().contains("Redis의 FlowDefinition"));
    }

    private FlowDefinition definition() {
        return new FlowDefinition(
                100L,
                1L,
                10L,
                "캐시 Flow",
                null,
                FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-24T00:00:00Z"),
                List.of(),
                List.of()
        );
    }
}
