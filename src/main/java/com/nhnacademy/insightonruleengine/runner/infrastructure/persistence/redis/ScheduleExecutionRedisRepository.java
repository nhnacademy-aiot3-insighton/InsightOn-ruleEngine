package com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis;

import com.nhnacademy.insightonruleengine.config.ScheduleExecutionProperties;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ScheduleExecutionRedisRepository {

    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";

    private static final DefaultRedisScript<Long> CHANGE_STATE_SCRIPT = new DefaultRedisScript<>("""
            local version = redis.call('INCR', KEYS[1])
            redis.call('HSET', KEYS[2], 'status', ARGV[1], 'version', version)
            return version
            """, Long.class);

    private static final DefaultRedisScript<Long> REPAIR_ACTIVE_SCRIPT = new DefaultRedisScript<>("""
            local status = redis.call('HGET', KEYS[1], 'status')
            if status == 'ACTIVE' then
                return 0
            end
            local currentVersion = tonumber(redis.call('HGET', KEYS[1], 'version') or '0')
            local reconciliationVersion = tonumber(ARGV[1])
            if currentVersion > reconciliationVersion then
                return 0
            end
            redis.call('HSET', KEYS[1], 'status', 'ACTIVE', 'version', reconciliationVersion)
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> MARK_INACTIVE_IF_PRESENT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 0 then
                return 0
            end
            local version = redis.call('INCR', KEYS[1])
            redis.call('HSET', KEYS[2], 'status', 'INACTIVE', 'version', version)
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> CLAIM_IF_ACTIVE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'status') ~= 'ACTIVE' then
                return 0
            end
            if redis.call('SET', KEYS[2], '1', 'PX', ARGV[1], 'NX') then
                return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory redisKeyFactory;
    private final ScheduleExecutionProperties properties;

    public long beginReconciliation() {
        Long version = redisTemplate.opsForValue().increment(redisKeyFactory.scheduleStateVersion());
        if (version == null) {
            throw new IllegalStateException("Schedule 상태 재조정 버전을 생성할 수 없습니다.");
        }
        return version;
    }

    public void markActive(Long flowId) {
        changeState(flowId, ACTIVE);
    }

    public void markInactive(Long flowId) {
        changeState(flowId, INACTIVE);
    }

    public void markInactiveIfPresent(Long flowId) {
        redisTemplate.execute(
                MARK_INACTIVE_IF_PRESENT_SCRIPT,
                List.of(
                        redisKeyFactory.scheduleStateVersion(),
                        redisKeyFactory.scheduleState(flowId)
                )
        );
    }

    public boolean repairActive(Long flowId, long reconciliationVersion) {
        Long repaired = redisTemplate.execute(
                REPAIR_ACTIVE_SCRIPT,
                List.of(redisKeyFactory.scheduleState(flowId)),
                Long.toString(reconciliationVersion)
        );
        return Long.valueOf(1L).equals(repaired);
    }

    public boolean claimIfActive(Long flowId, Instant scheduledAt) {
        Long claimed = redisTemplate.execute(
                CLAIM_IF_ACTIVE_SCRIPT,
                List.of(
                        redisKeyFactory.scheduleState(flowId),
                        redisKeyFactory.scheduleExecution(flowId, scheduledAt)
                ),
                Long.toString(properties.executionKeyTtl().toMillis())
        );
        return Long.valueOf(1L).equals(claimed);
    }

    private void changeState(Long flowId, String state) {
        redisTemplate.execute(
                CHANGE_STATE_SCRIPT,
                List.of(
                        redisKeyFactory.scheduleStateVersion(),
                        redisKeyFactory.scheduleState(flowId)
                ),
                state
        );
    }
}
