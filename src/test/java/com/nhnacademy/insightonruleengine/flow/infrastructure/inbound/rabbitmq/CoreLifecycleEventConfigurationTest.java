package com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq.CoreLifecycleEventProperties.EventBindingProperties;
import com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq.CoreLifecycleEventProperties.RetryProperties;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class CoreLifecycleEventConfigurationTest {

    @Test
    @DisplayName("하나의 Core Exchange에 그룹·장소 Queue와 각 DLQ를 모두 선언합니다")
    void declaresSharedCoreLifecycleTopologyTest() {
        CoreLifecycleEventProperties properties = new CoreLifecycleEventProperties(
                true,
                "insighton.core-events",
                new RetryProperties(3, Duration.ofSeconds(1), 2, Duration.ofSeconds(10)),
                new EventBindingProperties(
                        "group.deleted",
                        "rule-engine.group-deleted.queue",
                        "rule-engine.group-deleted.dlx",
                        "rule-engine.group-deleted.dlq",
                        "rule-engine.group-deleted.dlq"
                ),
                new EventBindingProperties(
                        "location.deleted",
                        "rule-engine.location-deleted.queue",
                        "rule-engine.location-deleted.dlx",
                        "rule-engine.location-deleted.dlq",
                        "rule-engine.location-deleted.dlq"
                )
        );

        Declarables topology = new CoreLifecycleEventConfiguration()
                .coreLifecycleEventTopology(properties);

        assertEquals(1, topology.getDeclarablesByType(TopicExchange.class).size());
        TopicExchange exchange = topology.getDeclarablesByType(TopicExchange.class).getFirst();
        assertEquals("insighton.core-events", exchange.getName());
        assertTrue(exchange.isDurable());
        assertFalse(exchange.isAutoDelete());

        Set<String> directExchangeNames = topology.getDeclarablesByType(DirectExchange.class).stream()
                .map(DirectExchange::getName)
                .collect(Collectors.toSet());
        assertEquals(
                Set.of("rule-engine.group-deleted.dlx", "rule-engine.location-deleted.dlx"),
                directExchangeNames
        );

        Set<String> queueNames = topology.getDeclarablesByType(Queue.class).stream()
                .map(Queue::getName)
                .collect(Collectors.toSet());
        assertEquals(
                Set.of(
                        "rule-engine.group-deleted.queue",
                        "rule-engine.group-deleted.dlq",
                        "rule-engine.location-deleted.queue",
                        "rule-engine.location-deleted.dlq"
                ),
                queueNames
        );
    }
}
