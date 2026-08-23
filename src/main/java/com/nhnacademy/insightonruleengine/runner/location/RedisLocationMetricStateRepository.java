package com.nhnacademy.insightonruleengine.runner.location;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.redis.RedisKeyFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/**
 * metric별 timestamp를 비교해 최신 값만 Redis에 남깁니다.
 * 값과 timestamp 갱신 및 전체 snapshot 조회는 Lua script 하나로 원자적으로 처리합니다.
 */
@Repository
@RequiredArgsConstructor
public class RedisLocationMetricStateRepository implements LocationMetricStateRepository {

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> MERGE_AND_GET_SCRIPT = new DefaultRedisScript<>("""
            for index = 1, #ARGV, 3 do
                local field = ARGV[index]
                local incomingTimestamp = tonumber(ARGV[index + 1])
                local currentTimestamp = tonumber(redis.call('HGET', KEYS[2], field))
                if (not currentTimestamp) or incomingTimestamp >= currentTimestamp then
                    redis.call('HSET', KEYS[1], field, ARGV[index + 2])
                    redis.call('HSET', KEYS[2], field, ARGV[index + 1])
                end
            end
            return redis.call('HGETALL', KEYS[1])
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisKeyFactory redisKeyFactory;

    @Override
    public Map<String, Object> mergeAndGet(SensorEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event는 필수입니다.");
        }
        Object[] arguments = arguments(event);
        @SuppressWarnings("unchecked")
        List<String> stored = redisTemplate.execute(
                MERGE_AND_GET_SCRIPT,
                List.of(
                        redisKeyFactory.locationMetrics(event.groupId(), event.locationId()),
                        redisKeyFactory.locationMetricTimestamps(event.groupId(), event.locationId())
                ),
                arguments
        );
        if (stored == null || stored.isEmpty()) {
            throw new IllegalStateException("Location metric 상태를 저장하지 못했습니다.");
        }
        return readSnapshot(stored);
    }

    private Object[] arguments(SensorEvent event) {
        Object[] arguments = new Object[event.metrics().size() * 3];
        int index = 0;
        for (Map.Entry<String, Object> metric : event.metrics().entrySet()) {
            arguments[index++] = metric.getKey();
            arguments[index++] = String.valueOf(event.timestamp().toEpochMilli());
            arguments[index++] = writeValue(metric.getValue());
        }
        return arguments;
    }

    private Map<String, Object> readSnapshot(List<String> stored) {
        if (stored.size() % 2 != 0) {
            throw new IllegalStateException("Redis Location metric snapshot 형식이 올바르지 않습니다.");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (int index = 0; index < stored.size(); index += 2) {
            snapshot.put(stored.get(index), readValue(stored.get(index + 1)));
        }
        return Map.copyOf(snapshot);
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Location metric 값을 저장할 수 없습니다.", exception);
        }
    }

    private Object readValue(String value) {
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Redis Location metric 값을 읽을 수 없습니다.", exception);
        }
    }
}
