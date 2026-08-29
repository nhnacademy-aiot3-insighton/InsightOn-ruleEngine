package com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RedisKeyFactoryTest {

    private final RedisKeyFactory keyFactory = new RedisKeyFactory();

    // Route와 Active Flow가 서로 침범하지 않는 확정 namespace를 검증합니다.
    @Test
    @DisplayName("Route와 Active Flow Key는 계약된 namespace와 ID 순서를 사용한다")
    void runtimeKeyNamespaceTest() {
        assertEquals("route:1:2", keyFactory.route(1L, 2L));
        assertEquals("active-flow:1:3", keyFactory.activeFlow(1L, 3L));
    }

    // Engine별 heartbeat가 독립된 Key로 저장되는지 검증합니다.
    @Test
    @DisplayName("heartbeat Key는 Engine ID를 namespace 뒤에 사용한다")
    void heartbeatKeyTest() {
        assertEquals("heartbeat:engine-a", keyFactory.heartbeat("engine-a"));
        assertEquals("heartbeat:engine-b", keyFactory.heartbeat("engine-b"));
    }

    // Redis Key에 잘못된 도메인 ID가 들어가지 않도록 모든 ID 위치를 검증합니다.
    @Test
    @DisplayName("Route와 Active Flow Key의 ID는 모두 양수여야 한다")
    void invalidRuntimeIdTest() {
        assertThrows(IllegalArgumentException.class, () -> keyFactory.route(null, 1L));
        assertThrows(IllegalArgumentException.class, () -> keyFactory.route(1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> keyFactory.activeFlow(-1L, 1L));
        assertThrows(IllegalArgumentException.class, () -> keyFactory.activeFlow(1L, null));
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
        assertThrows(IllegalArgumentException.class,
                () -> keyFactory.scheduleExecution(0L, Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> keyFactory.scheduleExecution(1L, null));
    }
}
