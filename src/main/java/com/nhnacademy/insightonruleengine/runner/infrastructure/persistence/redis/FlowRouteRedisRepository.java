package com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

//특정 그룹의 특정 장소에서 실행할 ACTIVE Flow Id 목록을 관리 예를 들어 활성화 상태인 Flow는 Redis에 저장, 나머지 상태는 Redis에 저장해줄 이유가 없습니다.
@Repository
@RequiredArgsConstructor
public class FlowRouteRedisRepository {
    //기존 목록 삭제와 새 목록 저장 사이에 다른 인스턴스가 Redis를 보는 순간이 있을 수 있는데 그걸 방지하기 위해
    //Lua script를 이용해 삭제와 저장을 묶어줬습니다.
    private static final DefaultRedisScript<Long> REPLACE_ROUTE_SCRIPT = new DefaultRedisScript<>(
            "redis.call('DEL', KEYS[1]); "
                    + "if #ARGV == 0 then return 0 end; "
                    + "for index = 1, #ARGV do redis.call('SADD', KEYS[1], ARGV[index]) end; "
                    + "return #ARGV",
            Long.class
    );
    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory redisKeyFactory;

    //특정 장소의 기존 FlowId 목록을 지우고 전달받은 새 목록으로 전환합니다.
    public void replace(Long groupId, Long locationId, Set<Long> flowIds) {
        validateFlowIds(flowIds);
        String key = redisKeyFactory.route(groupId, locationId);
        Object[] args = flowIds.stream()
                .map(String::valueOf)
                .toArray();
        redisTemplate.execute(
                REPLACE_ROUTE_SCRIPT,
                List.of(key),
                args
        );
    }

    //특정 장소의 FlowId 목록을 조회, 저장된 목록이 없으면 빈 Set 반환합니다.
    public Set<Long> findFlowIds(Long groupId, Long locationId) {
        String key = redisKeyFactory.route(groupId, locationId);
        Set<String> storedValues = redisTemplate.opsForSet().members(key);
        if (storedValues == null || storedValues.isEmpty()) {
            return Set.of();
        }
        Set<Long> flowIds = new HashSet<>();
        for (String storedValue : storedValues) {
            flowIds.add(parseFlowId(key, storedValue));
        }
        return Set.copyOf(flowIds);
    }

    //FlowId 목록을 읽지 않고 특정 장소의 RouteKey가 있는지 확인합니다.
    public boolean exists(Long groupId, Long locationId) {
        String key = redisKeyFactory.route(groupId, locationId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    //Flow 비활성화나 아카이브, 변경 및 삭제 시 해당 그룹과 장소의 Route 목록을 제거합니다.
    public void delete(Long groupId, Long locationId) {
        String key = redisKeyFactory.route(groupId, locationId);
        redisTemplate.delete(key);
    }

    //null, 0, 음수 Flow가 하나라도 존재할 시 Redis에 저장하기전에 거부합니다.
    private void validateFlowIds(Set<Long> flowIds) {
        if (flowIds == null
                || flowIds.stream().anyMatch(flowId -> flowId == null || flowId <= 0L)) {
            throw new IllegalArgumentException("라우터의 flowId는 양수여야합니다.");
        }
    }

    private Long parseFlowId(String key, String storedValue) {
        try {
            long flowId = Long.parseLong(storedValue);
            if (flowId <= 0L) {
                throw new NumberFormatException("non-positive value");
            }
            return flowId;
        } catch (NumberFormatException e) {
            throw new InvalidRouteDataException(
                    "Redis Route에 잘못된 형식의 Flow ID가 있습니다: " + key,
                    e
            );
        }
    }
}
