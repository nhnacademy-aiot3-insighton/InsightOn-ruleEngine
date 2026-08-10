package com.nhnacademy.insightonruleengine.runner.redis;

import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@NoArgsConstructor
@Component
public class RedisKeyFactory {

    private static final String ACTIVE_FLOW_KEY_FORMAT = "active-flow:%d:%d";
    private static final String HEARTBEAT_KEY_FORMAT = "heartbeat:%s";
    private static final String ROUTE_KEY_FORMAT = "route:%d:%d";

    //특정 그룹과 특정 장소를 하나의 active flow id 집합 키로 고정합니다.
    public String route(Long groupId, Long locationId) {
        validateId(groupId, "groupId");
        validateId(locationId, "locationId");
        return ROUTE_KEY_FORMAT.formatted(groupId, locationId);
    }

    public String activeFlow(Long groupId, Long flowId) {
        validateId(groupId, "groupId");
        validateId(flowId, "flowId");
        return ACTIVE_FLOW_KEY_FORMAT.formatted(groupId, flowId);
    }

    public String heartbeat(String engineId) {
        return HEARTBEAT_KEY_FORMAT.formatted(engineId);
    }

    private void validateId(Long id, String fileName) {
        if (id == null || id <= 0L) {
            throw new IllegalArgumentException(fileName + "는 양수여야 합니다.");
        }
    }
}
