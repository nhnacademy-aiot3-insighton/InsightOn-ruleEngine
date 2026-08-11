package com.nhnacademy.insightonruleengine.runner.heartbeat;

import com.nhnacademy.insightonruleengine.runner.redis.RedisKeyFactory;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EngineHeartbeatRepository {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory redisKeyFactory;

    //현재 엔진의 heartbeat 값과 만료 시간을 Redis에 저장합니다.
    public void refresh(String engineId, Duration ttl){
        if(ttl == null || ttl.isZero() || ttl.isNegative()){
            throw new IllegalArgumentException("heartbeat TTL은 양수여야 합니다.");
        }
        String key = redisKeyFactory.heartbeat(engineId);
        redisTemplate.opsForValue().set(key, engineId, ttl);
    }
    //heartbeat key가 있으면 true 없으면 false
    public boolean isHeartbeat(String engineId) {
        return redisTemplate.hasKey(redisKeyFactory.heartbeat(engineId));
    }
}
