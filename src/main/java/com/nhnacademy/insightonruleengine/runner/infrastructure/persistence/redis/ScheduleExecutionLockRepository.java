package com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis;

import com.nhnacademy.insightonruleengine.config.ScheduleExecutionProperties;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ScheduleExecutionLockRepository {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory redisKeyFactory;
    private final ScheduleExecutionProperties properties;

    public boolean acquire(Long flowId, Instant scheduledAt) {
        String key = redisKeyFactory.scheduleExecution(flowId, scheduledAt);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                key,
                "1",
                properties.executionKeyTtl()
        );
        return Boolean.TRUE.equals(acquired);
    }
}
