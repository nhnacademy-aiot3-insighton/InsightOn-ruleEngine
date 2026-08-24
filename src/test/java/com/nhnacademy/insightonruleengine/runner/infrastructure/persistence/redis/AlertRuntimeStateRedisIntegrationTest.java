package com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

//ALERT COUNT와 Cooldown을 실제 Redis에서 검증합니다.
@Testcontainers(disabledWithoutDocker = true)
class AlertRuntimeStateRedisIntegrationTest {

    private static final int REDIS_PORT = 6379;

    //JUnit Testcontainers 확장이 테스트 클래스 종료 시 컨테이너를 닫습니다.
    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(REDIS_PORT);

    private static AlertCountRedisRepository alertCountRedisRepository;
    private static LettuceConnectionFactory connectionFactory;
    private static RedisKeyFactory redisKeyFactory;
    private static StringRedisTemplate redisTemplate;

    //테스트용 Redis 연결을 설정합니다.
    @BeforeAll
    static void setUpRedis() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                REDIS.getHost(),
                REDIS.getMappedPort(REDIS_PORT)
        );
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisKeyFactory = new RedisKeyFactory();
        alertCountRedisRepository = new AlertCountRedisRepository(redisTemplate, redisKeyFactory);
    }

    //각 테스트가 이전 COUNT와 Cooldown의 영향을 받지 않도록 Redis를 비워줍니다.
    @AfterEach
    void clearRedis() {
        connectionFactory.getConnection().serverCommands().flushDb();
    }

    //테스트 연결을 닫고 Testcontainer에게 Container 정리를 맡깁니다.
    @AfterAll
    static void closeRedisConnection() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("COUNT는 고정 TTL 안에 임계값을 넘으면 true -> reset")
    void countThresholdResetTest() {
        assertFalse(alertCountRedisRepository.incrementAndCheck(100L, 20L, 3, 3, 0));
        String countKey = redisKeyFactory.count(100L, 20L);
        Long firstTtl = redisTemplate.getExpire(countKey, MILLISECONDS);
        assertTrue(firstTtl > 0L);

        await().pollDelay(Duration.ofMillis(300)).until(() -> true);
        assertFalse(alertCountRedisRepository.incrementAndCheck(100L, 20L, 3, 3, 0));
        Long secondTtl = redisTemplate.getExpire(countKey, MILLISECONDS);

        assertTrue(secondTtl > 0L);
        assertTrue(secondTtl <= firstTtl);

        assertTrue(alertCountRedisRepository.incrementAndCheck(100L, 20L, 3, 3, 0));
        assertFalse(redisTemplate.hasKey(countKey));
    }

    @Test
    @DisplayName("Cooldown 정책 상 Counter를 바로 변경하지 않고 TTL 만료 후 다음 Count를 합니다.")
    void cooldownTest() {
        String countKey = redisKeyFactory.count(100L, 20L);
        String cooldownKey = redisKeyFactory.cooldown(100L, 20L);
        assertFalse(alertCountRedisRepository.incrementAndCheck(100L, 20L, 2, 10, 1));
        assertTrue(alertCountRedisRepository.incrementAndCheck(100L, 20L, 2, 10, 1));
        assertFalse(alertCountRedisRepository.incrementAndCheck(100L, 20L, 2, 10, 1));

        assertFalse(redisTemplate.hasKey(countKey));
        assertTrue(redisTemplate.hasKey(cooldownKey));

        await().atMost(Duration.ofSeconds(3))
                .until(() -> !redisTemplate.hasKey(cooldownKey));
        assertFalse(alertCountRedisRepository.incrementAndCheck(100L, 20L, 2, 10, 1));
        assertEquals("1", redisTemplate.opsForValue().get(countKey));
    }

    @Test
    @DisplayName("동시 호출 중 한 번만 true를 반환하고 Cooldown을 생성합니다.")
    void concurrentCooldownTest() throws Exception {
        int requiredCount = 40;
        List<Callable<Boolean>> calls = IntStream.range(0, requiredCount)
                .mapToObj(index -> (Callable<Boolean>) () ->
                        alertCountRedisRepository.incrementAndCheck(100L, 10L, requiredCount, 30, 60))
                .toList();

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            long publishCount = executor.invokeAll(calls, 3, SECONDS).stream()
                    .filter(this::getResult)
                    .count();

            assertEquals(1L, publishCount);
        }
        assertTrue(redisTemplate.hasKey(redisKeyFactory.cooldown(100L, 10L)));
        assertFalse(redisTemplate.hasKey(redisKeyFactory.count(100L, 10L)));
    }

    //동시 실행 중 발생한 예외를 숨기지 않고 테스트 실패로 전달합니다.
    private boolean getResult(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalArgumentException("동시 Redis 작업 결과를 읽을 수 없습니다.", e);
        }
    }
}
