package com.nhnacademy.insightonruleengine.runner.infrastructure.outbound.rabbitmq;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.nhnacademy.insightonruleengine.config.ActionPublisherProperties;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.Severity;
import com.nhnacademy.insightonruleengine.runner.model.action.AiSuggestionActionEvent;
import com.nhnacademy.insightonruleengine.runner.model.action.EngineAlertActionEvent;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class RabbitActionPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RabbitActionPublisher publisher;
    private ActionPublisherProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ActionPublisherProperties(
                "insighton.rule-engine-events",
                "ai.alert.action",
                "ai.suggestion.action"
        );
        publisher = new RabbitActionPublisher(rabbitTemplate, properties);
    }

    @Test
    void publishesAlertToConfiguredRoute() {
        EngineAlertActionEvent event = event();

        assertDoesNotThrow(() -> publisher.publishAlert(event));

        verify(rabbitTemplate).convertAndSend(properties.exchange(), properties.alertRoutingKey(), event);
    }

    @Test
    void rejectsNullAlert() {
        assertThrows(IllegalArgumentException.class, () -> publisher.publishAlert(null));
    }

    @Test
    void convertsRabbitFailureToApplicationException() {
        EngineAlertActionEvent event = event();
        doThrow(new IllegalStateException("RabbitMQ unavailable"))
                .when(rabbitTemplate)
                .convertAndSend(properties.exchange(), properties.alertRoutingKey(), event);

        assertThrows(IllegalStateException.class, () -> publisher.publishAlert(event));
    }

    @Test
    void propagatesSuggestionPublishFailure() {
        AiSuggestionActionEvent event = new AiSuggestionActionEvent(
                1L,
                10L,
                100L,
                "temperature",
                31.5,
                OffsetDateTime.parse("2026-08-28T00:00:00Z")
        );
        doThrow(new IllegalStateException("RabbitMQ unavailable"))
                .when(rabbitTemplate)
                .convertAndSend(properties.exchange(), properties.suggestionRoutingKey(), event);

        assertThrows(IllegalStateException.class, () -> publisher.publishSuggestion(event));
    }

    private EngineAlertActionEvent event() {
        return new EngineAlertActionEvent(
                UUID.fromString("315efbba-2553-4d4d-bb67-f4f41a51f63a"),
                1L,
                10L,
                100L,
                "고온 경보",
                "온도가 기준을 초과했습니다.",
                Severity.CRITICAL,
                Map.of("temperature", 31.5)
        );
    }
}
