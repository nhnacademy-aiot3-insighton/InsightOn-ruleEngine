package com.nhnacademy.insightonruleengine.runner.heartbeat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HeartbeatPropertiesTest {

    // 활성화된 heartbeat의 확정된 엔진 ID와 시간 계약을 고정합니다.
    @Test
    @DisplayName("서로 다른 엔진 ID와 5초 갱신 주기 및 15초 TTL은 유효하다")
    void validConfigurationTest() {
        HeartbeatProperties properties = properties(true, "engine-a", "engine-b", 5, 15);

        assertDoesNotThrow(properties::validateConfiguration);
    }

    // 비활성 환경에서는 빈 설정값 때문에 애플리케이션이 실패하지 않게 합니다.
    @Test
    @DisplayName("heartbeat가 비활성화되면 나머지 설정을 검증하지 않는다")
    void disabledConfigurationTest() {
        HeartbeatProperties properties = new HeartbeatProperties(false, null, null, null, null);

        assertDoesNotThrow(properties::validateConfiguration);
    }

    // 두 엔진을 구분할 수 없는 모든 설정을 같은 시작 실패 계약으로 검증합니다.
    @ParameterizedTest
    @MethodSource("invalidEngineIds")
    @DisplayName("현재 엔진과 상대 엔진 ID는 비어 있지 않고 서로 달라야 한다")
    void invalidEngineIdTest(String engineId, String peerEngineId) {
        HeartbeatProperties properties = new HeartbeatProperties(
                true,
                engineId,
                peerEngineId,
                Duration.ofSeconds(5),
                Duration.ofSeconds(15)
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                properties::validateConfiguration
        );

        assertEquals(
                "서로 다른 현재 엔진과 상대 엔진의 아이디가 필요합니다.",
                exception.getMessage()
        );
    }

    // Location Affinity 운영 계약과 다른 갱신 주기가 시작 단계에서 거부되는지 보장합니다.
    @ParameterizedTest
    @MethodSource("invalidRefreshIntervals")
    @DisplayName("heartbeat 갱신 주기는 정확히 5초여야 한다")
    void invalidRefreshIntervalTest(Duration refreshInterval) {
        HeartbeatProperties properties = new HeartbeatProperties(
                true,
                "engine-a",
                "engine-b",
                refreshInterval,
                Duration.ofSeconds(15)
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                properties::validateConfiguration
        );

        assertEquals("heartbeat 갱신 주기는 5초여야 합니다.", exception.getMessage());
    }

    // 장애 판정 여유 시간을 바꾸는 잘못된 TTL이 적용되지 않도록 확정값을 검증합니다.
    @ParameterizedTest
    @MethodSource("invalidTtls")
    @DisplayName("heartbeat TTL은 정확히 15초여야 한다")
    void invalidTtlTest(Duration ttl) {
        HeartbeatProperties properties = new HeartbeatProperties(
                true,
                "engine-a",
                "engine-b",
                Duration.ofSeconds(5),
                ttl
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                properties::validateConfiguration
        );

        assertEquals("heartbeat TTL은 15초여야 합니다.", exception.getMessage());
    }

    // 여러 잘못된 엔진 ID 조합을 하나의 계약 테스트에 공급합니다.
    private static Stream<Arguments> invalidEngineIds() {
        return Stream.of(
                Arguments.of(null, "engine-b"),
                Arguments.of("", "engine-b"),
                Arguments.of(" ", "engine-b"),
                Arguments.of("engine-a", null),
                Arguments.of("engine-a", ""),
                Arguments.of("engine-a", " "),
                Arguments.of("engine-a", "engine-a")
        );
    }

    // null과 확정값이 아닌 갱신 주기를 함께 검증하도록 입력을 모아줍니다.
    private static Stream<Duration> invalidRefreshIntervals() {
        return Stream.of(null, Duration.ofSeconds(4), Duration.ofSeconds(6));
    }

    // null과 확정값이 아닌 TTL을 함께 검증하도록 입력을 모아줍니다.
    private static Stream<Duration> invalidTtls() {
        return Stream.of(null, Duration.ofSeconds(14), Duration.ofSeconds(16));
    }

    // 테스트마다 유효한 기본 설정을 반복하지 않도록 필요한 값만 받아 생성합니다.
    private HeartbeatProperties properties(
            boolean enabled,
            String engineId,
            String peerEngineId,
            long refreshSeconds,
            long ttlSeconds
    ) {
        return new HeartbeatProperties(
                enabled,
                engineId,
                peerEngineId,
                Duration.ofSeconds(refreshSeconds),
                Duration.ofSeconds(ttlSeconds)
        );
    }
}
