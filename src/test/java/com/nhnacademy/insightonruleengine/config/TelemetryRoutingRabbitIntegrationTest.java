package com.nhnacademy.insightonruleengine.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.IntStream;

import com.nhnacademy.insightonruleengine.config.TelemetryRoutingConfiguration;
import com.nhnacademy.insightonruleengine.config.TelemetryRoutingProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

//실제 RabbitMQ에서 동작하는지 확인합니다.
@Testcontainers(disabledWithoutDocker = true)
class TelemetryRoutingRabbitIntegrationTest {

    private static final int MESSAGE_COUNT = 5;
    private static final String TEST_ID = UUID.randomUUID().toString();
    private static final String EXCHANGE_NAME = "test.telemetry.exchange." + TEST_ID;
    private static final String QUEUE_PREFIX = "test.telemetry.queue." + TEST_ID + ".";

    //임시 RabbitMQ와 Consistent Hash 플러그인을 준비합니다.
    @Container
    private static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4.1-management-alpine")
    );

    private static RabbitAdmin rabbitAdmin;
    private static CachingConnectionFactory connectionFactory;
    private static RabbitTemplate rabbitTemplate;
    private static TelemetryRoutingProperties properties;

    @BeforeAll
    static void setUpRabbitMq() throws Exception {
        ExecResult pluginResult = RABBITMQ.execInContainer(
                "rabbitmq-plugins",
                "enable",
                "rabbitmq_consistent_hash_exchange"
        );
        assertEquals(0, pluginResult.getExitCode(), pluginResult.getStderr());

        connectionFactory = new CachingConnectionFactory(RABBITMQ.getHost(), RABBITMQ.getAmqpPort());
        connectionFactory.setUsername(RABBITMQ.getAdminUsername());
        connectionFactory.setPassword(RABBITMQ.getAdminPassword());
        connectionFactory.afterPropertiesSet();
        rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        properties = new TelemetryRoutingProperties(
                true,
                EXCHANGE_NAME,
                QUEUE_PREFIX,
                List.of(0, 2, 4, 6, 8, 10, 12, 14),
                ""
        );
        TelemetryRoutingConfiguration config = new TelemetryRoutingConfiguration();
        CustomExchange exchange = config.telemetryExchange(properties);
        Declarables topology = config.telemetryQueueTopology(exchange, properties);
        rabbitAdmin.declareExchange(exchange);
        declareTopology(topology);
    }

    //Spring AMQP가 사용한 연결을 닫고 Testcontainers에게 정리를 맡깁니다.
    @AfterAll
    static void closeConnection() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("같은 locationId로 발행한 메시지는 16개 Queue 중 같은 Queue가 수신합니다.")
    void sameLocationSameQueueTest() {
        List<String> expectedMessages = IntStream.range(0, MESSAGE_COUNT)
                .mapToObj(index -> "telemetry-" + index)
                .toList();
        expectedMessages.forEach(message -> rabbitTemplate.convertAndSend(EXCHANGE_NAME, "42", message));
        await().atMost(Duration.ofSeconds(5))
                .until(() -> totalMessageCount() == MESSAGE_COUNT);
        List<String> queuesWithMessages = queueNames().stream()
                .filter(queueName -> messageCount(queueName) > 0)
                .toList();
        assertEquals(1, queuesWithMessages.size());

        String selectedQueue = queuesWithMessages.getFirst();
        List<Object> actualMessages = IntStream.range(0, MESSAGE_COUNT)
                .mapToObj(index -> rabbitTemplate.receiveAndConvert(selectedQueue, 2_000L))
                .toList();
        assertEquals(expectedMessages, actualMessages);
    }

    //운영 설정이 만든 Queue와 Binding을 실제 RabbitMQ에 순서대로 등록합니다.
    private static void declareTopology(Declarables topology) {
        for (Declarable declarable : topology.getDeclarables()) {
            if (declarable instanceof Queue queue) {
                rabbitAdmin.declareQueue(queue);
            }
            if (declarable instanceof Binding binding) {
                rabbitAdmin.declareBinding(binding);
            }
        }
    }

    //16개 Queue 이름을 운영 설정과 같은 두 자리 번호 규칙으로 만듭니다.
    private static List<String> queueNames() {
        return IntStream.range(0, 16)
                .mapToObj(properties::queueName)
                .toList();
    }

    //RabbitMQ에서 받아온 Queue별 대기 메시지 수를 합산합니다.
    private static int totalMessageCount() {
        return queueNames().stream()
                .mapToInt(TelemetryRoutingRabbitIntegrationTest::messageCount)
                .sum();
    }

    //Queue가 실제로 선언됐는지 확인하고 현재 대기 메시지 수를 반환합니다.
    private static int messageCount(String queueName) {
        Properties queueProperties = rabbitAdmin.getQueueProperties(queueName);
        assertNotNull(queueProperties);
        return (Integer) queueProperties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
    }
}
