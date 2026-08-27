package com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis;

import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

//Redis에 값을 저장하고 찾을때 사용하는 key를 여기서 만듭니다.
@NoArgsConstructor
@Component
public class RedisKeyFactory {

    private static final String ACTIVE_FLOW_KEY_FORMAT = "active-flow:%d:%d";
    private static final String HEARTBEAT_KEY_FORMAT = "heartbeat:%s";
    private static final String ROUTE_KEY_FORMAT = "route:%d:%d";
    private static final String COUNT_KEY_FORMAT = "count:%d:%d";
    private static final String COOLDOWN_KEY_FORMAT = "cooldown:%d:%d";
    private static final String SCHEDULE_EXECUTION_KEY_FORMAT = "schedule-execution:%d:%d";

    private static final String FIELD_GROUP_ID = "groupId";
    private static final String FIELD_LOCATION_ID = "locationId";
    private static final String FIELD_FLOW_ID = "flowId";
    private static final String FIELD_ACTION_NODE_ID = "actionNodeId";
    private static final String FIELD_SCHEDULED_AT = "scheduledAt";

    //특정 그룹과 특정 장소를 하나의 active flow id 집합 키로 고정합니다.
    public String route(Long groupId, Long locationId) {
        validateId(groupId, FIELD_GROUP_ID);
        validateId(locationId, FIELD_LOCATION_ID);
        return ROUTE_KEY_FORMAT.formatted(groupId, locationId);
    }

    //특정 그룹의 ACTIVE Flow 실행 정보를 저장할 key를 만듭니다.
    public String activeFlow(Long groupId, Long flowId) {
        validateId(groupId, FIELD_GROUP_ID);
        validateId(flowId, FIELD_FLOW_ID);
        return ACTIVE_FLOW_KEY_FORMAT.formatted(groupId, flowId);
    }

    //각 엔진이 살아있는지 확인할 때 사용하는 heartbeat key를 만듭니다.
    public String heartbeat(String engineId) {
        validateEngineId(engineId);
        return HEARTBEAT_KEY_FORMAT.formatted(engineId);
    }

    //특정 Flow의 ALERT Action Node에 도달한 횟수를 저장할 Key를 만듭니다.
    public String count(Long flowId, Long actionNodeId) {
        validateId(flowId, FIELD_FLOW_ID);
        validateId(actionNodeId, FIELD_ACTION_NODE_ID);
        return COUNT_KEY_FORMAT.formatted(flowId, actionNodeId);
    }

    //특정 Flow의 ALERT Action Node가 연속적으로 알람을 보내진 않게 하기 위해 쿨다운 키를 만듭니다.
    public String cooldown(Long flowId, Long actionNodeId) {
        validateId(flowId, FIELD_FLOW_ID);
        validateId(actionNodeId, FIELD_ACTION_NODE_ID);
        return COOLDOWN_KEY_FORMAT.formatted(flowId, actionNodeId);
    }

    public String scheduleExecution(Long flowId, java.time.Instant scheduledAt) {
        validateId(flowId, FIELD_FLOW_ID);
        if (scheduledAt == null) {
            throw new IllegalArgumentException(FIELD_SCHEDULED_AT + "은 필수입니다.");
        }
        return SCHEDULE_EXECUTION_KEY_FORMAT.formatted(flowId, scheduledAt.getEpochSecond());
    }

    private void validateId(Long id, String fileName) {
        if (id == null || id <= 0L) {
            throw new IllegalArgumentException(fileName + "는 양수여야 합니다.");
        }
    }

    private void validateEngineId(String engineId) {
        if (engineId == null || engineId.isBlank()) {
            throw new IllegalArgumentException("engineId는 필수입니다.");
        }
    }
}
