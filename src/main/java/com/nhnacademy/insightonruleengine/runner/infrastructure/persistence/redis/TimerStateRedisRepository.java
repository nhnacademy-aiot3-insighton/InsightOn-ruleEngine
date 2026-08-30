package com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TimerStateRedisRepository {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory redisKeyFactory;

    public boolean acquire(Long nodeId, Long locationId, int intervalSeconds) {
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("intervalSeconds는 양수여야 합니다.");
        }
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                redisKeyFactory.timer(nodeId, locationId),
                "1",
                Duration.ofSeconds(intervalSeconds)
        );
        return Boolean.TRUE.equals(acquired);
    }
}
