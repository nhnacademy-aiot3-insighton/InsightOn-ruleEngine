package com.nhnacademy.insightonruleengine.runner.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 장소 단위의 실행 가능한 Flow 목록을 하나의 Redis 값으로 관리한다.
 * Definition과 라우팅 인덱스를 분리하지 않아 갱신 중 부분 상태를 줄인다.
 */
@Component
@ConditionalOnProperty(
        name = "rule-engine.flow-cache.type",
        havingValue = "redis",
        matchIfMissing = true
)
public class RedisFlowDefinitionCache implements FlowDefinitionCache {

    /**
     * EVENT_GATE 전환 이전 snapshot과 직렬화 계약이 섞이지 않도록 cache namespace를 버전으로 분리한다.
     * 구 버전인 rule-engine:flow-route:*는 배포 절차에서 별도로 정리하며 이 구현에서는 읽지 않는다.
     */
    private static final String KEY_PREFIX = "rule-engine:v2:flow-route:";
    private static final TypeReference<List<FlowDefinition>> FLOW_LIST_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisFlowDefinitionCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${rule-engine.flow-cache.ttl:30m}") Duration ttl
    ) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Flow 캐시 TTL은 양수여야 합니다.");
        }
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    @Override
    public Optional<List<FlowDefinition>> find(Long groupId, Long locationId) {
        String value = redisTemplate.opsForValue().get(key(groupId, locationId));
        if (value == null) {
            return Optional.empty();
        }
        List<FlowDefinition> definitions = read(value);
        validateDefinitions(groupId, locationId, definitions);
        return Optional.of(definitions);
    }

    @Override
    public void replace(Long groupId, Long locationId, List<FlowDefinition> definitions) {
        validateDefinitions(groupId, locationId, definitions);
        String value = write(definitions);
        redisTemplate.opsForValue().set(key(groupId, locationId), value, ttl);
    }

    @Override
    public void evict(Long groupId, Long locationId) {
        redisTemplate.delete(key(groupId, locationId));
    }

    private String key(Long groupId, Long locationId) {
        return KEY_PREFIX + groupId + ":" + locationId;
    }

    private String write(List<FlowDefinition> definitions) {
        try {
            return objectMapper.writeValueAsString(definitions);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("FlowDefinition을 Redis 형식으로 변환할 수 없습니다.", exception);
        }
    }

    private List<FlowDefinition> read(String value) {
        try {
            return List.copyOf(objectMapper.readValue(value, FLOW_LIST_TYPE));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Redis의 FlowDefinition을 읽을 수 없습니다.", exception);
        }
    }

    private void validateDefinitions(
            Long groupId,
            Long locationId,
            List<FlowDefinition> definitions
    ) {
        if (groupId == null || locationId == null || definitions == null) {
            throw new IllegalArgumentException("Flow 캐시의 라우트와 정의 목록은 필수입니다.");
        }
        Set<Long> flowIds = new HashSet<>();
        for (FlowDefinition definition : definitions) {
            if (definition == null
                    || definition.flowId() == null
                    || definition.flowId() <= 0L
                    || !groupId.equals(definition.groupId())
                    || !locationId.equals(definition.locationId())
                    || definition.status() != FlowStatus.ACTIVE
                    || !flowIds.add(definition.flowId())) {
                throw new IllegalStateException(
                        "Redis FlowDefinition이 라우트 또는 ACTIVE 계약과 일치하지 않습니다."
                );
            }
        }
    }
}
