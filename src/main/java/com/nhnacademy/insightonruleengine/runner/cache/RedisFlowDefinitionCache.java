package com.nhnacademy.insightonruleengine.runner.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 장소 단위의 실행 가능한 Flow 목록을 하나의 Redis 값으로 관리한다.
 * Definition과 라우팅 인덱스를 분리하지 않아 갱신 중 부분 상태를 줄인다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "rule-engine.flow-cache.type",
        havingValue = "redis",
        matchIfMissing = true
)
public class RedisFlowDefinitionCache implements FlowDefinitionCache {

    private static final String KEY_PREFIX = "rule-engine:flow-route:";
    private static final TypeReference<List<FlowDefinition>> FLOW_LIST_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<List<FlowDefinition>> find(Long groupId, Long locationId) {
        String value = redisTemplate.opsForValue().get(key(groupId, locationId));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(read(value));
    }

    @Override
    public void replace(Long groupId, Long locationId, List<FlowDefinition> definitions) {
        String value = write(definitions);
        redisTemplate.opsForValue().set(key(groupId, locationId), value);
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
}
