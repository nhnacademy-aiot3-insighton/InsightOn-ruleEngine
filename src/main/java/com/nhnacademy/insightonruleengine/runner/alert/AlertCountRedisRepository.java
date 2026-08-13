package com.nhnacademy.insightonruleengine.runner.alert;

import com.nhnacademy.insightonruleengine.runner.redis.RedisKeyFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

//ALERT Action Node에 도달한 횟수 카운트해서 Redis에 누적시킵니다.
@Repository
@RequiredArgsConstructor
public class AlertCountRedisRepository {

    private static final DefaultRedisScript<Long> TRANSITION_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then
                return 0
            end

            local requiredCount = tonumber(ARGV[1])
            local countTimeoutSeconds = tonumber(ARGV[2])
            local cooldownSeconds = tonumber(ARGV[3])

            if requiredCount == 1 then
                if cooldownSeconds > 0 then
                    redis.call('SET', KEYS[2], '1', 'EX', cooldownSeconds)
                end
                return 1
            end

            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], countTimeoutSeconds)
            end

            if count < requiredCount then
                return 0
            end

            redis.call('DEL', KEYS[1])
            if cooldownSeconds > 0 then
                redis.call('SET', KEYS[2], '1', 'EX', cooldownSeconds)
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory redisKeyFactory;

    //Cooldown 확인부터 Counter 처리와 새 Cooldown 생성까지 한 Redis 연산으로 수행합니다
    public boolean incrementAndCheck(
            Long flowId,
            Long alertActionNodeId,
            int requiredCount,
            int countTimeoutSeconds,
            int cooldownSeconds
    ) {
        String countKey = redisKeyFactory.count(flowId, alertActionNodeId);
        String cooldownKey = redisKeyFactory.cooldown(flowId, alertActionNodeId);
        Long result = redisTemplate.execute(
                TRANSITION_SCRIPT,
                List.of(countKey, cooldownKey),
                String.valueOf(requiredCount),
                String.valueOf(countTimeoutSeconds),
                String.valueOf(cooldownSeconds)
        );

        return Long.valueOf(1L).equals(result);
    }
}
