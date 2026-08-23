package com.nhnacademy.insightonruleengine.runner.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.LinkDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.runner.alert.AlertCountRedisRepository;
import com.nhnacademy.insightonruleengine.runner.heartbeat.EngineHeartbeatRepository;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.location.RedisLocationMetricStateRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static FlowRouteRedisRepository routeRepository;
    private static ActiveFlowRedisRepository activeFlowRepository;
    private static EngineHeartbeatRepository heartbeatRepository;
    private static AlertCountRedisRepository alertCountRedisRepository;
    private static RedisLocationMetricStateRepository locationMetricStateRepository;

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
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        routeRepository = new FlowRouteRedisRepository(redisTemplate, keyFactory);
        activeFlowRepository = new ActiveFlowRedisRepository(redisTemplate, objectMapper, keyFactory);
        heartbeatRepository = new EngineHeartbeatRepository(redisTemplate, keyFactory);
        alertCountRedisRepository = new AlertCountRedisRepository(redisTemplate, keyFactory);
        locationMetricStateRepository = new RedisLocationMetricStateRepository(
                redisTemplate, objectMapper, keyFactory);
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

    // Lua 기반 교체가 중간 빈 상태 없이 최종 Set으로 반영되고 삭제되는지 검증합니다.
    @Test
    @DisplayName("Route를 원자적으로 저장하고 조회 및 교체한 뒤 삭제합니다.")
    void routeLifecycleTest() {
        routeRepository.replace(1L, 10L, Set.of(100L, 200L));

        assertTrue(routeRepository.exists(1L, 10L));
        assertEquals(Set.of(100L, 200L), routeRepository.findFlowIds(1L, 10L));

        routeRepository.replace(1L, 10L, Set.of(300L));

        assertEquals(Set.of(300L), routeRepository.findFlowIds(1L, 10L));

        routeRepository.delete(1L, 10L);

        assertFalse(routeRepository.exists(1L, 10L));
        assertEquals(Set.of(), routeRepository.findFlowIds(1L, 10L));
    }

    // Flow와 모든 NodeType 및 Link가 실제 Redis JSON에서 손실 없이 복원되는지 검증합니다.
    @Test
    @DisplayName("Flow Node Link Definition과 모든 NodeType을 JSON으로 저장하고 복원합니다.")
    void activeFlowSerializationTest() {
        FlowDefinition definition = definition();

        activeFlowRepository.save(definition);

        assertTrue(activeFlowRepository.exists(1L, 100L));
        assertEquals(definition, activeFlowRepository.getActiveFlow(1L, 100L).orElseThrow());

        activeFlowRepository.delete(1L, 100L);

        assertFalse(activeFlowRepository.exists(1L, 100L));
        assertTrue(activeFlowRepository.getActiveFlow(1L, 100L).isEmpty());
    }

    // Route와 Active Flow 및 heartbeat가 한 Redis에서도 서로 다른 namespace를 쓰는지 확인합니다.
    @Test
    @DisplayName("Route Active Flow heartbeat Key는 하나의 Redis에서 서로 격리됩니다")
    void namespaceIsolationTest() {
        routeRepository.replace(1L, 10L, Set.of(100L));
        activeFlowRepository.save(definition());
        heartbeatRepository.refresh("engine-a", Duration.ofSeconds(15));

        assertEquals(Set.of(
                "route:1:10",
                "active-flow:1:100",
                "heartbeat:engine-a"
        ), redisTemplate.keys("*"));
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
    @DisplayName("ALERT는 requiredCount 도달 시 한 번 허용하고 Counter를 초기화합니다")
    void alertRequiredCountTest() {
        assertFalse(alertCountRedisRepository.incrementAndCheck(1L, 10L, 3, 30, 0));
        assertFalse(alertCountRedisRepository.incrementAndCheck(1L, 10L, 3, 30, 0));
        assertTrue(alertCountRedisRepository.incrementAndCheck(1L, 10L, 3, 30, 0));

        assertFalse(redisTemplate.hasKey("count:1:10"));
        assertFalse(alertCountRedisRepository.incrementAndCheck(1L, 10L, 3, 30, 0));
    }

    @Test
    @DisplayName("Location metric은 센서가 달라도 metric별 최신 timestamp 값으로 병합됩니다")
    void latestLocationMetricStateTest() {
        locationMetricStateRepository.mergeAndGet(event(
                1L, 10L, 100L, "2026-08-11T00:00:02Z", Map.of("temperature", 25.0)));
        Map<String, Object> snapshot = locationMetricStateRepository.mergeAndGet(event(
                1L, 10L, 200L, "2026-08-11T00:00:01Z", Map.of("humidity", 60)));

        assertEquals(Map.of("temperature", 25.0, "humidity", 60), snapshot);

        Map<String, Object> afterOlderPacket = locationMetricStateRepository.mergeAndGet(event(
                1L, 10L, 200L, "2026-08-11T00:00:00Z", Map.of("temperature", 10.0)));
        assertEquals(25.0, afterOlderPacket.get("temperature"));
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

    // enum 전체가 포함된 실제 실행 모델로 Redis 직렬화 계약을 검증합니다.
    private FlowDefinition definition() {
        List<NodeDefinition> nodes = Arrays.stream(NodeType.values())
                .map(nodeType -> new NodeDefinition(
                        (long) nodeType.ordinal() + 1L,
                        nodeType,
                        JsonNodeFactory.instance.objectNode().put("type", nodeType.name())
                ))
                .toList();
        List<LinkDefinition> links = List.of(new LinkDefinition(1L, 100L, 1L, 2L, "out", "in"));
        return new FlowDefinition(
                100L,
                1L,
                10L,
                "온도 경고",
                "Redis 직렬화 검증",
                FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-11T00:00:00Z"),
                nodes,
                links
        );
    }

    private SensorEvent event(
            Long groupId,
            Long locationId,
            Long sensorId,
            String timestamp,
            Map<String, Object> metrics
    ) {
        return new SensorEvent(groupId, locationId, sensorId, metrics, Instant.parse(timestamp));
    }
}
