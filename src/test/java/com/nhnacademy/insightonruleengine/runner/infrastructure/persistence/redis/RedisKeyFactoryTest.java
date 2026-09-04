package com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RedisKeyFactoryTest {

    private final RedisKeyFactory keyFactory = new RedisKeyFactory();

    // Engine별 heartbeat가 독립된 Key로 저장되는지 검증합니다.
    @Test
    @DisplayName("heartbeat Key는 Engine ID를 namespace 뒤에 사용한다")
    void heartbeatKeyTest() {
        assertEquals("heartbeat:engine-a", keyFactory.heartbeat("engine-a"));
        assertEquals("heartbeat:engine-b", keyFactory.heartbeat("engine-b"));
    }

    // 공백 Engine ID가 공용 heartbeat Key로 합쳐지는 것을 방지합니다.
    @Test
    @DisplayName("heartbeat Key의 Engine ID는 비어 있을 수 없다")
    void invalidEngineIdTest() {
        assertThrows(IllegalArgumentException.class, () -> keyFactory.heartbeat(null));
        assertThrows(IllegalArgumentException.class, () -> keyFactory.heartbeat(""));
        assertThrows(IllegalArgumentException.class, () -> keyFactory.heartbeat(" "));
    }

    @Test
    @DisplayName("Schedule 실행 Key는 Flow와 예정 실행 초를 구분한다")
    void scheduleExecutionKeyTest() {
        assertEquals("schedule-state:10", keyFactory.scheduleState(10L));
        assertEquals("schedule-state-version", keyFactory.scheduleStateVersion());
        assertEquals(
                "schedule-execution:10:1787558400",
                keyFactory.scheduleExecution(10L, Instant.parse("2026-08-24T08:00:00Z"))
        );
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> keyFactory.scheduleExecution(0L, now));
        assertThrows(IllegalArgumentException.class,
                () -> keyFactory.scheduleExecution(1L, null));
    }

}
