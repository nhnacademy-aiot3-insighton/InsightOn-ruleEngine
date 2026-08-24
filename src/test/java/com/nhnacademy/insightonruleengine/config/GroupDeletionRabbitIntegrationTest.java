package com.nhnacademy.insightonruleengine.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.nhnacademy.insightonruleengine.flow.application.cleanup.GroupDeletionCleanupService;
import com.nhnacademy.insightonruleengine.runner.infrastructure.inbound.rabbitmq.GroupDeletionListener;
import com.nhnacademy.insightonruleengine.runner.infrastructure.inbound.rabbitmq.LocationDeletionListener;
import com.nhnacademy.insightonruleengine.runner.model.GroupDeletedEvent;
import com.nhnacademy.insightonruleengine.runner.model.LocationDeletedEvent;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = GroupDeletionRabbitIntegrationTest.TestApplication.class
)
@TestPropertySource(
        locations = "classpath:config/prod/rule-engine.properties",
        properties = {
                "rule-engine.group-deletion.initial-interval=10ms",
                "rule-engine.group-deletion.max-interval=20ms",
                "rule-engine.location-deletion.initial-interval=10ms",
                "rule-engine.location-deletion.max-interval=20ms"
        }
)
class GroupDeletionRabbitIntegrationTest {

    private static final String EXCHANGE = "insighton.core-events";
    private static final String ROUTING_KEY = "group.deleted";
    private static final String QUEUE = "rule-engine.group-deleted.queue";
    private static final String DEAD_LETTER_QUEUE = "rule-engine.group-deleted.dlq";
    private static final String AI_GROUP_QUEUE = "ai-service.group-deleted.queue";
    private static final String LOCATION_ROUTING_KEY = "location.deleted";
    private static final String LOCATION_QUEUE = "rule-engine.location-deleted.queue";
    private static final String LOCATION_DEAD_LETTER_QUEUE = "rule-engine.location-deleted.dlq";
    private static final String AI_LOCATION_QUEUE = "ai-service.location-deleted.queue";

    @Container
    private static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4.1-management-alpine")
    );

    @MockitoBean
    private GroupDeletionCleanupService cleanupService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin rabbitAdmin;

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    @BeforeEach
    void purgeQueues() {
        purgeQueueAndWait(QUEUE);
        purgeQueueAndWait(DEAD_LETTER_QUEUE);
        purgeQueueAndWait(LOCATION_QUEUE);
        purgeQueueAndWait(LOCATION_DEAD_LETTER_QUEUE);
        purgeQueueAndWait(AI_GROUP_QUEUE);
        purgeQueueAndWait(AI_LOCATION_QUEUE);
    }

    private void purgeQueueAndWait(String queueName) {
        rabbitAdmin.purgeQueue(queueName, false);
    }

    @Test
    @DisplayName("Core의 group.deleted 이벤트를 전용 Queue에서 한 번 처리합니다")
    void consumeCoreGroupDeletedEventTest() {
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, event(10L));

        verify(cleanupService, timeout(5_000)).cleanup(10L, List.of(100L, 200L));
        assertNull(rabbitTemplate.receive(DEAD_LETTER_QUEUE, 200L));
        Message aiMessage = rabbitTemplate.receive(AI_GROUP_QUEUE, 2_000L);
        assertNotNull(aiMessage);
        assertEquals(
                MessageDeliveryMode.PERSISTENT,
                aiMessage.getMessageProperties().getReceivedDeliveryMode()
        );
    }

    @Test
    @DisplayName("정리 실패는 세 번만 시도한 뒤 영속 DLQ로 이동합니다")
    void boundedRetryThenDeadLetterTest() {
        doThrow(new IllegalStateException("cleanup failed"))
                .when(cleanupService).cleanup(20L, List.of(100L, 200L));

        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, event(20L));

        verify(cleanupService, timeout(5_000).times(3)).cleanup(20L, List.of(100L, 200L));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertNotNull(rabbitTemplate.receive(DEAD_LETTER_QUEUE, 200L))
        );
    }

    @Test
    @DisplayName("Core의 location.deleted 이벤트를 전용 Queue에서 한 번 처리합니다")
    void consumeCoreLocationDeletedEventTest() {
        rabbitTemplate.convertAndSend(
                EXCHANGE,
                LOCATION_ROUTING_KEY,
                new LocationDeletedEvent(30L)
        );

        verify(cleanupService, timeout(5_000)).cleanupLocation(30L);
        assertNull(rabbitTemplate.receive(LOCATION_DEAD_LETTER_QUEUE, 200L));
        assertNotNull(rabbitTemplate.receive(AI_LOCATION_QUEUE, 2_000L));
    }

    @Test
    @DisplayName("장소 정리 실패도 세 번만 시도한 뒤 전용 영속 DLQ로 이동합니다")
    void locationBoundedRetryThenDeadLetterTest() {
        doThrow(new IllegalStateException("cleanup failed"))
                .when(cleanupService).cleanupLocation(40L);

        rabbitTemplate.convertAndSend(
                EXCHANGE,
                LOCATION_ROUTING_KEY,
                new LocationDeletedEvent(40L)
        );

        verify(cleanupService, timeout(5_000).times(3)).cleanupLocation(40L);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertNotNull(rabbitTemplate.receive(LOCATION_DEAD_LETTER_QUEUE, 200L))
        );
    }

    @Test
    @DisplayName("같은 그룹 삭제 이벤트가 중복 전달되어도 두 번 정상 처리합니다")
    void duplicateGroupEventTest() {
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, event(50L));
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, event(50L));

        verify(cleanupService, timeout(5_000).times(2))
                .cleanup(50L, List.of(100L, 200L));
        assertNotNull(rabbitTemplate.receive(AI_GROUP_QUEUE, 2_000L));
        assertNotNull(rabbitTemplate.receive(AI_GROUP_QUEUE, 2_000L));
    }

    private GroupDeletedEvent event(Long groupId) {
        return new GroupDeletedEvent(groupId, List.of(100L, 200L));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
    })
    @Import({
            RabbitMessageConverterConfig.class,
            GroupDeletionConfiguration.class,
            GroupDeletionListener.class,
            LocationDeletionConfiguration.class,
            LocationDeletionListener.class
    })
    static class TestApplication {

        @Bean
        Queue aiGroupDeletedQueue() {
            return new Queue(AI_GROUP_QUEUE, true);
        }

        @Bean
        Queue aiLocationDeletedQueue() {
            return new Queue(AI_LOCATION_QUEUE, true);
        }

        @Bean
        Binding aiGroupDeletedBinding(
                @Qualifier("aiGroupDeletedQueue") Queue queue
        ) {
            return new Binding(
                    queue.getName(),
                    Binding.DestinationType.QUEUE,
                    EXCHANGE,
                    ROUTING_KEY,
                    null
            );
        }

        @Bean
        Binding aiLocationDeletedBinding(
                @Qualifier("aiLocationDeletedQueue") Queue queue
        ) {
            return new Binding(
                    queue.getName(),
                    Binding.DestinationType.QUEUE,
                    EXCHANGE,
                    LOCATION_ROUTING_KEY,
                    null
            );
        }
    }
}
