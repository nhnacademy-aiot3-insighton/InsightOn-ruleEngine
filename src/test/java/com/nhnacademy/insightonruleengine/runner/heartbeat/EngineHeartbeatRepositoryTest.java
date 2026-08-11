package com.nhnacademy.insightonruleengine.runner.heartbeat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.runner.redis.RedisKeyFactory;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class EngineHeartbeatRepositoryTest {

    private static final String ENGINE_ID = "engine-a";
    private static final String HEARTBEAT_KEY = "heartbeat:engine-a";
    private static final Duration TTL = Duration.ofSeconds(15);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedisKeyFactory redisKeyFactory;

    private EngineHeartbeatRepository heartbeatRepository;

    // 각 테스트가 같은 Repository 의존성 조합에서 동작하도록 대상을 준비합니다.
    @BeforeEach
    void setUp() {
        heartbeatRepository = new EngineHeartbeatRepository(redisTemplate, redisKeyFactory);
    }

    // heartbeat 값과 TTL이 같은 Redis 명령으로 저장되는지 검증합니다.
    @Test
    @DisplayName("heartbeat를 엔진 ID 값과 15초 TTL로 저장한다")
    void refreshHeartbeatTest() {
        when(redisKeyFactory.heartbeat(ENGINE_ID)).thenReturn(HEARTBEAT_KEY);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        heartbeatRepository.refresh(ENGINE_ID, TTL);

        verify(valueOperations).set(HEARTBEAT_KEY, ENGINE_ID, TTL);
    }

    // 만료되지 않는 heartbeat가 만들어지지 않도록 잘못된 TTL을 Redis 호출 전에 차단합니다.
    @Test
    @DisplayName("null이거나 0 이하인 TTL은 Redis에 저장하지 않는다")
    void invalidTtlTest() {
        assertThrows(IllegalArgumentException.class, () -> heartbeatRepository.refresh(ENGINE_ID, null));
        assertThrows(IllegalArgumentException.class, () -> heartbeatRepository.refresh(ENGINE_ID, Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> heartbeatRepository.refresh(ENGINE_ID, Duration.ofSeconds(-1))
        );
        verifyNoInteractions(redisTemplate, redisKeyFactory);
    }

    // Redis Key 존재 여부가 상대 엔진 생존 여부로 그대로 전달되는지 확인합니다.
    @Test
    @DisplayName("heartbeat Key 존재 여부를 반환한다")
    void heartbeatExistenceTest() {
        when(redisKeyFactory.heartbeat(ENGINE_ID)).thenReturn(HEARTBEAT_KEY);
        when(redisTemplate.hasKey(HEARTBEAT_KEY)).thenReturn(true, false);

        assertTrue(heartbeatRepository.isHeartbeat(ENGINE_ID));
        assertFalse(heartbeatRepository.isHeartbeat(ENGINE_ID));
    }

    // Redis 장애가 heartbeat 미존재로 바뀌어 잘못된 Queue 인계를 일으키지 않게 합니다.
    @Test
    @DisplayName("Redis 조회 장애는 heartbeat 미존재로 변환하지 않는다")
    void redisFailureTest() {
        when(redisKeyFactory.heartbeat(ENGINE_ID)).thenReturn(HEARTBEAT_KEY);
        when(redisTemplate.hasKey(HEARTBEAT_KEY))
                .thenThrow(new RedisConnectionFailureException("Redis 연결 실패"));

        assertThrows(
                RedisConnectionFailureException.class,
                () -> heartbeatRepository.isHeartbeat(ENGINE_ID)
        );
    }
}
