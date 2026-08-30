package com.nhnacademy.insightonruleengine.runner.application.telemetry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.nhnacademy.insightonruleengine.config.TelemetryRoutingProperties;
import com.nhnacademy.insightonruleengine.heartbeat.EngineHeartbeatRepository;
import com.nhnacademy.insightonruleengine.heartbeat.EngineHeartbeatService;
import com.nhnacademy.insightonruleengine.heartbeat.HeartbeatProperties;
import com.nhnacademy.insightonruleengine.runner.infrastructure.inbound.rabbitmq.TelemetryListenerContainerManager;
import com.nhnacademy.insightonruleengine.runner.infrastructure.inbound.rabbitmq.TelemetryMessageConsumer;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.RedisKeyFactory;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class TelemetryFailoverIntegrationTest {

    private static final String TEST_ID = UUID.randomUUID().toString();
    private static final String QUEUE_PREFIX = "test.telemetry.failover." + TEST_ID + ".";
    private static final List<Integer> ENGINE_B_QUEUE_INDICES =
            List.of(1, 3, 5, 7, 9, 11, 13, 15);
    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(15);

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    @Container
    private static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4.1-management-alpine")
    );

    private static LettuceConnectionFactory redisConnectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static CachingConnectionFactory rabbitConnectionFactory;
    private static RabbitAdmin rabbitAdmin;
    private static RedisKeyFactory redisKeyFactory;
    private static TelemetryRoutingProperties routingProperties;
    private static HeartbeatProperties heartbeatProperties;

    private TelemetryListenerContainerManager listenerContainerManager;
    private EngineHeartbeatRepository heartbeatRepository;
    private TelemetryQueueFailoverMonitor failoverMonitor;

    @BeforeAll
    static void setUpInfrastructure() {
        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration(
                REDIS.getHost(),
                REDIS.getMappedPort(6379)
        );
        redisConnectionFactory = new LettuceConnectionFactory(redisConfiguration);
        redisConnectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(redisConnectionFactory);
        redisTemplate.afterPropertiesSet();
        redisKeyFactory = new RedisKeyFactory();

        rabbitConnectionFactory = new CachingConnectionFactory(
                RABBITMQ.getHost(),
                RABBITMQ.getAmqpPort()
        );
        rabbitConnectionFactory.setUsername(RABBITMQ.getAdminUsername());
        rabbitConnectionFactory.setPassword(RABBITMQ.getAdminPassword());
        rabbitConnectionFactory.afterPropertiesSet();
        rabbitAdmin = new RabbitAdmin(rabbitConnectionFactory);

        routingProperties = new TelemetryRoutingProperties(
                true,
                "test.telemetry.failover.exchange." + TEST_ID,
                QUEUE_PREFIX,
                ENGINE_B_QUEUE_INDICES,
                "locationId"
        );
        heartbeatProperties = new HeartbeatProperties(
                true,
                "engine-b",
                "engine-a",
                Duration.ofSeconds(5),
                HEARTBEAT_TTL
        );
        queueNames().forEach(queueName -> rabbitAdmin.declareQueue(
                new Queue(queueName, true, false, false)
        ));
    }

    @BeforeEach
    void setUpFailover() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        heartbeatRepository = new EngineHeartbeatRepository(redisTemplate, redisKeyFactory);
        EngineHeartbeatService heartbeatService = new EngineHeartbeatService(
                heartbeatRepository,
                heartbeatProperties
        );
        listenerContainerManager = new TelemetryListenerContainerManager(
                rabbitConnectionFactory,
                routingProperties,
                mock(TelemetryMessageConsumer.class),
                rabbitAdmin
        );
        failoverMonitor = new TelemetryQueueFailoverMonitor(
                heartbeatService,
                listenerContainerManager,
                routingProperties,
                heartbeatProperties
        );
        listenerContainerManager.startNormal();
        awaitConsumerCount(routingProperties.ownedQueueNames(), 1);
        awaitConsumerCount(routingProperties.peerQueueNames(), 0);
    }

    @AfterEach
    void stopListeners() {
        if (listenerContainerManager == null) {
            return;
        }
        listenerContainerManager.stopAll();
        awaitConsumerCount(queueNames(), 0);
    }

    @AfterAll
    static void closeInfrastructure() {
        rabbitConnectionFactory.destroy();
        redisConnectionFactory.destroy();
    }

    @Test
    void peerHeartbeatLossTakesOverQueuesAndRecoveryHandsThemBack() {
        heartbeatRepository.refresh("engine-a", HEARTBEAT_TTL);

        failoverMonitor.checkPeerStatus();

        assertFalse(listenerContainerManager.isTakingOver());
        redisTemplate.delete(redisKeyFactory.heartbeat("engine-a"));

        failoverMonitor.checkPeerStatus();

        assertTrue(listenerContainerManager.isTakingOver());
        awaitConsumerCount(routingProperties.peerQueueNames(), 1);
        awaitConsumerCount(routingProperties.ownedQueueNames(), 1);

        heartbeatRepository.refresh("engine-a", HEARTBEAT_TTL);
        failoverMonitor.checkPeerStatus();

        assertFalse(listenerContainerManager.isTakingOver());
        awaitConsumerCount(routingProperties.peerQueueNames(), 0);
        awaitConsumerCount(routingProperties.ownedQueueNames(), 1);
    }

    private static void awaitConsumerCount(List<String> queueNames, int expectedCount) {
        await().atMost(Duration.ofSeconds(10))
                .until(() -> queueNames.stream()
                        .allMatch(queueName -> consumerCount(queueName) == expectedCount));
    }

    private static int consumerCount(String queueName) {
        Properties queueProperties = rabbitAdmin.getQueueProperties(queueName);
        if (queueProperties == null) {
            return -1;
        }
        return (Integer) queueProperties.get(RabbitAdmin.QUEUE_CONSUMER_COUNT);
    }

    private static List<String> queueNames() {
        return IntStream.range(0, 16)
                .mapToObj(routingProperties::queueName)
                .toList();
    }
}
