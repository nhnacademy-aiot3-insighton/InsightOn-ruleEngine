package com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.config.ScheduleExecutionProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class ScheduleExecutionRedisRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private ScheduleExecutionRedisRepository repository;

    @BeforeEach
    void setUp() {
        ScheduleExecutionProperties properties = new ScheduleExecutionProperties(
                "Asia/Seoul",
                Duration.ofMinutes(10),
                2
        );
        repository = new ScheduleExecutionRedisRepository(
                redisTemplate,
                new RedisKeyFactory(),
                properties
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsReconciliationVersionAndUpdatesSharedScheduleState() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("schedule-state-version")).thenReturn(5L);

        assertEquals(5L, repository.beginReconciliation());
        repository.markActive(10L);
        repository.markInactive(10L);
        repository.markInactiveIfPresent(20L);

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("schedule-state-version", "schedule-state:10")),
                eq("ACTIVE")
        );
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("schedule-state-version", "schedule-state:10")),
                eq("INACTIVE")
        );
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("schedule-state-version", "schedule-state:20"))
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void repairsOnlyMissingOrOlderActiveState() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("schedule-state:10")),
                eq("5")
        )).thenReturn(1L, 0L);

        assertTrue(repository.repairActive(10L, 5L));
        assertFalse(repository.repairActive(10L, 5L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void onlyActiveScheduleCanBeClaimedByOneEngine() {
        Instant scheduledAt = Instant.parse("2026-08-24T08:00:00Z");
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("schedule-state:10", "schedule-execution:10:1787558400")),
                eq("600000")
        )).thenReturn(1L, 0L);

        assertTrue(repository.claimIfActive(10L, scheduledAt));
        assertFalse(repository.claimIfActive(10L, scheduledAt));
    }
}
