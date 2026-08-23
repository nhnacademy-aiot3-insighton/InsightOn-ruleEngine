package com.nhnacademy.insightonruleengine.runner.heartbeat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import com.nhnacademy.insightonruleengine.heartbeat.EngineHeartbeatRepository;
import com.nhnacademy.insightonruleengine.heartbeat.EngineHeartbeatService;
import com.nhnacademy.insightonruleengine.heartbeat.EngineStatus;
import com.nhnacademy.insightonruleengine.heartbeat.HeartbeatProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

@ExtendWith(MockitoExtension.class)
class EngineHeartbeatServiceTest {

    private static final String ENGINE_ID = "engine-a";
    private static final String PEER_ENGINE_ID = "engine-b";
    private static final Duration TTL = Duration.ofSeconds(15);

    @Mock
    private EngineHeartbeatRepository heartbeatRepository;

    private EngineHeartbeatService heartbeatService;

    @BeforeEach
    void setUp() {
        HeartbeatProperties properties = new HeartbeatProperties(
                true,
                ENGINE_ID,
                PEER_ENGINE_ID,
                Duration.ofSeconds(5),
                TTL
        );
        heartbeatService = new EngineHeartbeatService(heartbeatRepository, properties);
    }

    // 현재 엔진의 heartbeat만 정해진 TTL로 갱신하는지 확인합니다.
    @Test
    @DisplayName("현재 엔진 heartbeat를 15초 TTL로 갱신한다")
    void refreshHeartbeatTest() {
        heartbeatService.refreshHeartbeat();

        verify(heartbeatRepository).refresh(ENGINE_ID, TTL);
    }

    // 상대 Key가 있을 때 정상 상태로 판단하는 계약을 검증합니다.
    @Test
    @DisplayName("상대 heartbeat가 존재하면 UP을 반환한다")
    void peerUpTest() {
        when(heartbeatRepository.isHeartbeat(PEER_ENGINE_ID)).thenReturn(true);

        assertEquals(EngineStatus.UP, heartbeatService.getEngineStatus());
    }

    // 정상적인 Key 만료만 상대 장애 상태로 판단하는 계약을 검증합니다.
    @Test
    @DisplayName("상대 heartbeat가 만료되면 DOWN을 반환한다")
    void peerDownTest() {
        when(heartbeatRepository.isHeartbeat(PEER_ENGINE_ID)).thenReturn(false);

        assertEquals(EngineStatus.DOWN, heartbeatService.getEngineStatus());
    }

    // Redis 장애를 상대 엔진 DOWN으로 오판하지 않도록 예외 전파를 고정합니다.
    @Test
    @DisplayName("Redis 장애는 상대 엔진 DOWN으로 변환하지 않는다")
    void redisFailureTest() {
        when(heartbeatRepository.isHeartbeat(PEER_ENGINE_ID))
                .thenThrow(new RedisConnectionFailureException("Redis 연결 실패"));

        assertThrows(RedisConnectionFailureException.class, heartbeatService::getEngineStatus);
    }
}
