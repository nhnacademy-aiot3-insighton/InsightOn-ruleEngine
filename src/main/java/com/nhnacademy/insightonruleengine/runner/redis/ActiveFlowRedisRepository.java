package com.nhnacademy.insightonruleengine.runner.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ActiveFlowRedisRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisKeyFactory redisKeyFactory;

    //ACTIVE 상태의 FlowDefinition만 Json으로 Redis에 저장
    public void save(FlowDefinition flowDefinition) {
        if (flowDefinition == null) {
            throw new IllegalArgumentException("FlowDefinition은 필수입니다.");
        }
        if (flowDefinition.status() != FlowStatus.ACTIVE) {
            throw new IllegalArgumentException("Flow Status는 Active 상태여야 합니다.");
        }
        String key = redisKeyFactory.activeFlow(flowDefinition.groupId(), flowDefinition.flowId());
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(flowDefinition));
        } catch (JsonProcessingException e) {
            throw new InvalidActiveFlowDataException(
                    "FlowDefinition을 Json 변환할 수 없습니다.",
                    e
            );
        }
    }

    //ACTIVE 상태의 FlowDefinition을 조회
    public Optional<FlowDefinition> getActiveFlow(Long groupId, Long flowId) {
        String key = redisKeyFactory.activeFlow(groupId, flowId);
        String storedValues = redisTemplate.opsForValue().get(key);
        if (storedValues == null || storedValues.isEmpty()) {
            return Optional.empty();
        }
        try {
            FlowDefinition definition = objectMapper.readValue(storedValues, FlowDefinition.class);
            validateKey(groupId, flowId, definition);
            return Optional.of(definition);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidActiveFlowDataException(
                    "Redis Active FlowDefinition Json이 올바르지 않습니다.",
                    e
            );
        }
    }

    //Json 전체를 읽지 않고 특정 ACTIVE Flow Key가 있는지 확인합니다.
    public boolean exists(Long groupId, Long flowId) {
        String key = redisKeyFactory.activeFlow(groupId, flowId);
        return redisTemplate.hasKey(key);
    }

    //Flow가 비활성화나 아카이브, 변경 및 삭제 시 해당 그룹과 장소의 Route 목록을 제거합니다.
    public void delete(Long groupId, Long flowId) {
        String key = redisKeyFactory.activeFlow(groupId, flowId);
        redisTemplate.delete(key);
    }

    //조회한 Key의 그룹, FlowId와 Json 안의 그룹, FlowId가 같은지 확인합니다.
    private void validateKey(Long groupId, Long flowId, FlowDefinition flowDefinition) {
        if (flowDefinition == null
                || flowDefinition.status() != FlowStatus.ACTIVE
                || !flowDefinition.groupId().equals(groupId)
                || !flowDefinition.flowId().equals(flowId)) {
            throw new IllegalArgumentException("Redis Key와 FlowDefinition이 일치하지 않습니다.");
        }
    }
}
