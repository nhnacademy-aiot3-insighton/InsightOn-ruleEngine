package com.nhnacademy.insightonruleengine.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nhnacademy.insightonruleengine.heartbeat.EngineHeartbeatRepository;
import com.nhnacademy.insightonruleengine.heartbeat.EngineHeartbeatService;
import com.nhnacademy.insightonruleengine.heartbeat.HeartbeatProperties;
import com.nhnacademy.insightonruleengine.heartbeat.HeartbeatScheduler;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.RedisKeyFactory;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HeartbeatConfigurationTest {

    @Mock
    private EngineHeartbeatRepository heartbeatRepository;

    @Mock
    private RedisKeyFactory redisKeyFactory;

    private final HeartbeatConfiguration configuration = new HeartbeatConfiguration();

    // Bean 생성 전에 설정과 두 Engine Key가 모두 검증되는지 확인합니다.
    @Test
    @DisplayName("유효한 설정으로 현재 및 상대 Engine Key를 검증하고 Service를 생성한다")
    void heartbeatServiceTest() {
        HeartbeatProperties properties = properties(Duration.ofSeconds(5), Duration.ofSeconds(15));

        EngineHeartbeatService service = configuration.engineHeartbeatService(
                heartbeatRepository,
                properties,
                redisKeyFactory
        );

        assertNotNull(service);
        verify(redisKeyFactory).heartbeat("engine-a");
        verify(redisKeyFactory).heartbeat("engine-b");
    }

    // 잘못된 시간 설정에서는 일부 Bean만 만들어진 애플리케이션이 시작되지 않게 합니다.
    @Test
    @DisplayName("잘못된 heartbeat 설정이면 Service Bean을 생성하지 않는다")
    void invalidHeartbeatServiceTest() {
        HeartbeatProperties properties = properties(Duration.ofSeconds(4), Duration.ofSeconds(15));

        assertThrows(
                IllegalStateException.class,
                () -> configuration.engineHeartbeatService(heartbeatRepository, properties, redisKeyFactory)
        );
        verifyNoInteractions(redisKeyFactory);
    }

    // Scheduler Bean이 검증을 마친 Service를 그대로 사용하도록 구성되는지 확인합니다.
    @Test
    @DisplayName("heartbeat Service로 Scheduler Bean을 생성한다")
    void heartbeatSchedulerTest() {
        EngineHeartbeatService service = new EngineHeartbeatService(heartbeatRepository, properties(
                Duration.ofSeconds(5),
                Duration.ofSeconds(15)
        ));

        HeartbeatScheduler scheduler = configuration.heartbeatScheduler(service);

        assertNotNull(scheduler);
    }

    // 테스트가 시간 값만 바꾸면서 같은 Engine 식별 계약을 사용하도록 설정을 생성합니다.
    private HeartbeatProperties properties(Duration refreshInterval, Duration ttl) {
        return new HeartbeatProperties(true, "engine-a", "engine-b", refreshInterval, ttl);
    }
}
