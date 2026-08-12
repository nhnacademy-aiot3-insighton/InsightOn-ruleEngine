package com.nhnacademy.insightonruleengine.runner.alert;

import com.nhnacademy.insightonruleengine.runner.redis.RedisKeyFactory;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

//ALERT Action Node의 Cooldown을 Redis에 저장합니다.
@Repository
@RequiredArgsConstructor
public class AlertCooldownRedisRepository {

    private static final String ACQUIRED_VALUE = "1";

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory redisKeyFactory;

    public boolean tryAcquire(Long flowId, Long alertActionNodeId, Duration duration) {
        String key = redisKeyFactory.cooldown(flowId, alertActionNodeId);
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, ACQUIRED_VALUE, duration));
    }

}
