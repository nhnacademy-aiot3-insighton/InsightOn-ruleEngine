package com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis;

import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

//Redis에 값을 저장하고 찾을때 사용하는 key를 여기서 만듭니다.
@NoArgsConstructor
@Component
public class RedisKeyFactory {

    private static final String HEARTBEAT_KEY_FORMAT = "heartbeat:%s";
    private static final String COUNT_KEY_FORMAT = "count:%d:%d";
    private static final String COOLDOWN_KEY_FORMAT = "cooldown:%d:%d";
    private static final String SCHEDULE_STATE_KEY_FORMAT = "schedule-state:%d";
    private static final String SCHEDULE_STATE_VERSION_KEY = "schedule-state-version";
    private static final String SCHEDULE_EXECUTION_KEY_FORMAT = "schedule-execution:%d:%d";

    private static final String FIELD_FLOW_ID = "flowId";
    private static final String FIELD_ACTION_NODE_ID = "actionNodeId";
    private static final String FIELD_SCHEDULED_AT = "scheduledAt";

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

    public String scheduleState(Long flowId) {
        validateId(flowId, FIELD_FLOW_ID);
        return SCHEDULE_STATE_KEY_FORMAT.formatted(flowId);
    }

    public String scheduleStateVersion() {
        return SCHEDULE_STATE_VERSION_KEY;
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
