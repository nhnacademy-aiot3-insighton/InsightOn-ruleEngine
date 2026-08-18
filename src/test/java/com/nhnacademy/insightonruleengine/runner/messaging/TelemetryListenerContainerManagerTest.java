package com.nhnacademy.insightonruleengine.runner.messaging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.nhnacademy.insightonruleengine.common.config.TelemetryRoutingProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

class TelemetryListenerContainerManagerTest {

    private final ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    private final TelemetryMessageConsumer messageConsumer = mock(TelemetryMessageConsumer.class);

    @Test
    @DisplayName("라우팅 활성화되면 인스턴스 생성시 정상 컨테이너와 인계 컨테이너가 준비됩니다.")
    void lifecycleTest() {
        TelemetryRoutingProperties properties = new TelemetryRoutingProperties(
                true,
                "insighton.core.telemetry.exchange",
                "telemetry.",
                List.of(0, 2, 4, 6, 8, 10, 12, 14),
                null
        );
        TelemetryListenerContainerManager manager = new TelemetryListenerContainerManager(
                connectionFactory,
                properties,
                messageConsumer
        );
        assertFalse(manager.isNormalRunning());
        assertFalse(manager.isTakingOver());

        // startNormal, takeover, handback, stopNormal 멱등 동작 확인합니다.
        manager.startNormal();
        manager.startNormal(); // 중복 호출에도 안전한지 확인합니다.
        assertTrue(manager.isNormalRunning());

        manager.takeover();
        manager.takeover(); // 중복 인계에도 안전한지 확인합니다.
        assertTrue(manager.isTakingOver());

        manager.handback();
        manager.handback(); // 중복 반환에도 안전한지 확인합니다.
        assertFalse(manager.isTakingOver());
        assertTrue(manager.isNormalRunning());

        manager.stopNormal();
        assertFalse(manager.isNormalRunning());
    }

    @Test
    @DisplayName("라우팅이 비활성화되면 컨테이너가 시작되지 않습니다.")
    void disabledRoutingTest() {
        TelemetryRoutingProperties properties = new TelemetryRoutingProperties(
                false,
                "insighton.core.telemtry.exchange",
                "telemetry.",
                List.of(0, 2, 4, 6, 8, 10, 12, 14),
                null
        );
        TelemetryListenerContainerManager manager = new TelemetryListenerContainerManager(
                connectionFactory,
                properties,
                messageConsumer
        );
        manager.startNormal();
        manager.takeover();
        assertFalse(manager.isNormalRunning());
        assertFalse(manager.isTakingOver());
    }
}
