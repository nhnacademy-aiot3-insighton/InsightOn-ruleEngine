package com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhnacademy.insightonruleengine.config.ScheduleExecutionProperties;
import com.nhnacademy.insightonruleengine.heartbeat.EngineHeartbeatRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RedisRuntimeRepositoryIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisKeyFactory keyFactory;
    private static EngineHeartbeatRepository heartbeatRepository;
    private static AlertCountRedisRepository alertCountRedisRepository;
    private static ScheduleExecutionRedisRepository scheduleExecutionRedisRepository;
    private static TimerStateRedisRepository timerStateRedisRepository;

    // 실제 Redis 명령과 JSON 변환을 함께 검증할 Repository들을 컨테이너에 연결합니다.
    @BeforeAll
    static void setUpRedis() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                REDIS.getHost(),
                REDIS.getMappedPort(6379)
        );
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        keyFactory = new RedisKeyFactory();
        heartbeatRepository = new EngineHeartbeatRepository(redisTemplate, keyFactory);
        alertCountRedisRepository = new AlertCountRedisRepository(redisTemplate, keyFactory);
        scheduleExecutionRedisRepository = new ScheduleExecutionRedisRepository(
                redisTemplate,
                keyFactory,
                new ScheduleExecutionProperties("Asia/Seoul", Duration.ofMinutes(10), 2)
        );
        timerStateRedisRepository = new TimerStateRedisRepository(redisTemplate, keyFactory);
    }

    // 테스트 간 Redis Key가 남아 결과를 바꾸지 않도록 매번 현재 DB만 비워줍니다.
    @BeforeEach
    void clearRedisTestData() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    // Lettuce 자원이 전체 테스트 종료 뒤 남지 않도록 연결을 닫습니다.
    @AfterAll
    static void closeRedisConnection() {
        connectionFactory.destroy();
    }

    // heartbeat가 실제 TTL을 가지며 만료 후 상대 생존 조회에서 사라지는지 검증합니다.
    @Test
    @DisplayName("heartbeat는 TTL과 함께 저장되고 만료 후 존재하지 않습니다.")
    void heartbeatExpirationTest() {
        heartbeatRepository.refresh("engine-a", Duration.ofMillis(300));

        Long ttl = redisTemplate.getExpire("heartbeat:engine-a", TimeUnit.MILLISECONDS);

        assertTrue(heartbeatRepository.isHeartbeat("engine-a"));
        assertTrue(ttl != null && ttl > 0L && ttl <= 300L);
        org.testcontainers.shaded.org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(() -> !heartbeatRepository.isHeartbeat("engine-a"));
        assertFalse(heartbeatRepository.isHeartbeat("engine-a"));
    }

    @Test
    @DisplayName("ACTIVE Schedule의 동일 실행 시각은 한 인스턴스만 선점합니다")
    void scheduleExecutionClaimTest() {
        Instant scheduledAt = Instant.parse("2026-08-24T08:00:00Z");

        scheduleExecutionRedisRepository.markInactiveIfPresent(99L);
        assertFalse(redisTemplate.hasKey("schedule-state:99"));
        assertFalse(scheduleExecutionRedisRepository.claimIfActive(10L, scheduledAt));

        scheduleExecutionRedisRepository.markActive(10L);

        assertTrue(scheduleExecutionRedisRepository.claimIfActive(10L, scheduledAt));
        assertFalse(scheduleExecutionRedisRepository.claimIfActive(10L, scheduledAt));

        scheduleExecutionRedisRepository.markInactive(10L);

        assertFalse(scheduleExecutionRedisRepository.claimIfActive(10L, scheduledAt.plusSeconds(60)));
    }

    @Test
    @DisplayName("비활성화보다 오래된 재조정은 Schedule 상태를 다시 ACTIVE로 만들 수 없습니다")
    void staleScheduleReconciliationDoesNotOverrideInactiveState() {
        scheduleExecutionRedisRepository.markActive(10L);
        long staleReconciliationVersion = scheduleExecutionRedisRepository.beginReconciliation();

        scheduleExecutionRedisRepository.markInactive(10L);

        assertFalse(scheduleExecutionRedisRepository.repairActive(10L, staleReconciliationVersion));
        assertFalse(scheduleExecutionRedisRepository.claimIfActive(
                10L,
                Instant.parse("2026-08-24T08:01:00Z")
        ));

        long newerReconciliationVersion = scheduleExecutionRedisRepository.beginReconciliation();

        assertTrue(scheduleExecutionRedisRepository.repairActive(10L, newerReconciliationVersion));
        assertTrue(scheduleExecutionRedisRepository.claimIfActive(
                10L,
                Instant.parse("2026-08-24T08:02:00Z")
        ));
    }

    @Test
    @DisplayName("ALERT는 requiredCount 도달 시 한 번 허용하고 Counter를 초기화합니다")
    void alertRequiredCountTest() {
        assertFalse(alertCountRedisRepository.incrementAndCheck(1L, 10L, 3, 30, 0));
        assertFalse(alertCountRedisRepository.incrementAndCheck(1L, 10L, 3, 30, 0));
        assertTrue(alertCountRedisRepository.incrementAndCheck(1L, 10L, 3, 30, 0));

        assertFalse(redisTemplate.hasKey("count:1:10"));
        assertFalse(alertCountRedisRepository.incrementAndCheck(1L, 10L, 3, 30, 0));
    }

    @Test
    @DisplayName("Flow Runtime State 정리는 전달한 Node ID의 COUNT와 Cooldown만 삭제합니다")
    void deleteFlowRuntimeStateTest() {
        redisTemplate.opsForValue().set("count:1:10", "2");
        redisTemplate.opsForValue().set("cooldown:1:10", "1");
        redisTemplate.opsForValue().set("count:1:20", "1");
        redisTemplate.opsForValue().set("cooldown:2:10", "1");

        alertCountRedisRepository.deleteStates(1L, Set.of(10L));

        assertFalse(redisTemplate.hasKey("count:1:10"));
        assertFalse(redisTemplate.hasKey("cooldown:1:10"));
        assertTrue(redisTemplate.hasKey("count:1:20"));
        assertTrue(redisTemplate.hasKey("cooldown:2:10"));
    }

    @Test
    @DisplayName("Timer는 두 인스턴스의 동시 요청 중 하나만 통과시키고 TTL 후 다시 통과시킵니다")
    void timerAtomicIntervalTest() {
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<Boolean> first = acquireAfter(start);
        CompletableFuture<Boolean> second = acquireAfter(start);

        start.countDown();

        assertTrue(first.join() ^ second.join());
        assertFalse(timerStateRedisRepository.acquire(100L, 20L, 1));
        org.testcontainers.shaded.org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(() -> !redisTemplate.hasKey("timer:100:20"));
        assertTrue(timerStateRedisRepository.acquire(100L, 20L, 1));
    }

    private CompletableFuture<Boolean> acquireAfter(CountDownLatch start) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                start.await();
                return timerStateRedisRepository.acquire(100L, 20L, 1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("동시 실행 테스트가 중단됐습니다.", exception);
            }
        });
    }

}
