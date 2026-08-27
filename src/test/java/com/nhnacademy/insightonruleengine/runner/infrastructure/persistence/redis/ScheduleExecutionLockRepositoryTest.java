package com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.config.ScheduleExecutionProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class ScheduleExecutionLockRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private ScheduleExecutionLockRepository repository;

    @BeforeEach
    void setUp() {
        ScheduleExecutionProperties properties = new ScheduleExecutionProperties(
                "Asia/Seoul",
                Duration.ofMinutes(10),
                2
        );
        repository = new ScheduleExecutionLockRepository(
                redisTemplate,
                new RedisKeyFactory(),
                properties
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void onlyFirstEngineAcquiresTheScheduledOccurrence() {
        Instant scheduledAt = Instant.parse("2026-08-24T08:00:00Z");
        when(valueOperations.setIfAbsent(
                "schedule-execution:10:1787558400",
                "1",
                Duration.ofMinutes(10)
        )).thenReturn(true, false);

        assertTrue(repository.acquire(10L, scheduledAt));
        assertFalse(repository.acquire(10L, scheduledAt));
    }
}
