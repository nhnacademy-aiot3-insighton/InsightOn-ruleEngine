package com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/**
 * EVENT_GATE Node의 도달 횟수와 쿨다운을 (flow_id, node_id)별로 관리한다.
 *
 * <p>쿨다운 확인 → 횟수 증가 → 도달 시 초기화 → 쿨다운 시작을 한 Lua로 처리한다. 두 연산으로 나누면
 * 쿨다운 중에도 횟수가 쌓여 억제가 끝나자마자 통과해 버리므로, 원자성이 곧 동작 정의다.
 */
@Repository
@RequiredArgsConstructor
public class EventGateStateRedisRepository {

    private static final DefaultRedisScript<Long> TRANSITION_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then
                return 0
            end

            local requiredCount = tonumber(ARGV[1])
            local countWindowSeconds = tonumber(ARGV[2])
            local cooldownSeconds = tonumber(ARGV[3])

            if requiredCount == 1 then
                if cooldownSeconds > 0 then
                    redis.call('SET', KEYS[2], '1', 'EX', cooldownSeconds)
                end
                return 1
            end

            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], countWindowSeconds)
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

    /** 이번 실행을 통과시킬지 판정하고, 통과하면 다음 주기를 위해 상태를 갱신한다. */
    public boolean tryPass(
            Long flowId,
            Long gateNodeId,
            int requiredCount,
            int countWindowSeconds,
            int cooldownSeconds
    ) {
        Long result = redisTemplate.execute(
                TRANSITION_SCRIPT,
                List.of(
                        redisKeyFactory.count(flowId, gateNodeId),
                        redisKeyFactory.cooldown(flowId, gateNodeId)
                ),
                String.valueOf(requiredCount),
                String.valueOf(countWindowSeconds),
                String.valueOf(cooldownSeconds)
        );
        return Long.valueOf(1L).equals(result);
    }

    /** Flow 관련 런타임 상태 정리 시 미리 수집한 EVENT_GATE Node Id의 횟수와 쿨다운을 삭제한다. */
    public void deleteStates(Long flowId, Set<Long> gateNodeIds) {
        if (gateNodeIds == null || gateNodeIds.isEmpty()) {
            return;
        }
        List<String> keys = gateNodeIds.stream()
                .flatMap(gateNodeId -> Stream.of(
                        redisKeyFactory.count(flowId, gateNodeId),
                        redisKeyFactory.cooldown(flowId, gateNodeId)
                ))
                .toList();
        redisTemplate.delete(keys);
    }
}
