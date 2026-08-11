package com.nhnacademy.insightonruleengine.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;

class TelemetryRoutingConfigurationTest {

    private static final String EXCHANGE_NAME = "insighton.core.telemetry.exchange";

    private final TelemetryRoutingConfiguration configuration = new TelemetryRoutingConfiguration();

    // Location Affinity를 만드는 Exchange의 종류와 내구성 계약을 고정합니다.
    @Test
    @DisplayName("Telemetry Exchange는 durable x-consistent-hash로 선언한다")
    void consistentHashExchangeTest() {
        CustomExchange exchange = configuration.telemetryExchange(properties(null));

        assertEquals(EXCHANGE_NAME, exchange.getName());
        assertEquals("x-consistent-hash", exchange.getType());
        assertTrue(exchange.isDurable());
        assertFalse(exchange.isAutoDelete());
        assertTrue(exchange.getArguments().isEmpty());
    }

    // Hash Header 방식이 선택되면 Exchange 인수로 전달되는지 확인합니다.
    @Test
    @DisplayName("설정된 Hash Header를 consistent-hash Exchange 인수에 전달한다")
    void hashHeaderTest() {
        CustomExchange exchange = configuration.telemetryExchange(properties("locationId"));

        assertEquals("locationId", exchange.getArguments().get("hash-header"));
    }

    // 고정 16개 Queue와 weight 1 Binding이 모두 생성되는지 검증합니다.
    @Test
    @DisplayName("00부터 15까지 durable Queue 16개를 weight 1로 고정 Binding한다")
    void fixedQueueTopologyTest() {
        TelemetryRoutingProperties properties = properties(null);
        CustomExchange exchange = configuration.telemetryExchange(properties);

        Declarables topology = configuration.telemetryQueueTopology(exchange, properties);
        List<Queue> queues = topology.getDeclarablesByType(Queue.class);
        List<Binding> bindings = topology.getDeclarablesByType(Binding.class);

        assertEquals(16, queues.size());
        assertEquals(16, bindings.size());
        for (int index = 0; index < 16; index++) {
            Queue queue = queues.get(index);
            Binding binding = bindings.get(index);
            String expectedQueueName = "telemetry.%02d".formatted(index);

            assertEquals(expectedQueueName, queue.getName());
            assertTrue(queue.isDurable());
            assertEquals(expectedQueueName, binding.getDestination());
            assertEquals(EXCHANGE_NAME, binding.getExchange());
            assertEquals("1", binding.getRoutingKey());
        }
    }

    // 테스트마다 동일한 이슈 7 Routing 설정을 사용하도록 Fixture를 생성합니다.
    private TelemetryRoutingProperties properties(String hashHeader) {
        return new TelemetryRoutingProperties(
                true,
                EXCHANGE_NAME,
                "telemetry.",
                List.of(0, 2, 4, 6, 8, 10, 12, 14),
                hashHeader
        );
    }
}
