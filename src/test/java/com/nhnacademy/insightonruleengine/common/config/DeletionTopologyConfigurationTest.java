package com.nhnacademy.insightonruleengine.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.TopicExchange;

class DeletionTopologyConfigurationTest {

    @Test
    @DisplayName("그룹 삭제 토폴로지는 Core 이벤트 Exchange를 직접 선언합니다")
    void groupTopologyDeclaresCoreEventsExchangeTest() {
        GroupDeletionProperties properties = new GroupDeletionProperties(
                true,
                "insighton.core-events",
                "group.deleted",
                "rule-engine.group-deleted.queue",
                "rule-engine.group-deleted.dlx",
                "rule-engine.group-deleted.dlq",
                "rule-engine.group-deleted.dlq",
                3,
                Duration.ofSeconds(1),
                2,
                Duration.ofSeconds(10)
        );

        Declarables topology = new GroupDeletionConfiguration().groupDeletionTopology(properties);

        assertCoreEventsExchange(topology);
    }

    @Test
    @DisplayName("장소 삭제 토폴로지도 독립적으로 Core 이벤트 Exchange를 선언합니다")
    void locationTopologyDeclaresCoreEventsExchangeTest() {
        LocationDeletionProperties properties = new LocationDeletionProperties(
                true,
                "insighton.core-events",
                "location.deleted",
                "rule-engine.location-deleted.queue",
                "rule-engine.location-deleted.dlx",
                "rule-engine.location-deleted.dlq",
                "rule-engine.location-deleted.dlq",
                3,
                Duration.ofSeconds(1),
                2,
                Duration.ofSeconds(10)
        );

        Declarables topology = new LocationDeletionConfiguration().locationDeletionTopology(properties);

        assertCoreEventsExchange(topology);
    }

    private void assertCoreEventsExchange(Declarables topology) {
        TopicExchange exchange = topology.getDeclarablesByType(TopicExchange.class).getFirst();
        assertEquals("insighton.core-events", exchange.getName());
        assertTrue(exchange.isDurable());
        assertFalse(exchange.isAutoDelete());
    }
}
