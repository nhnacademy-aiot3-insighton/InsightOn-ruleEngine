package com.nhnacademy.insightonruleengine.runner.infrastructure.outbound.rabbitmq;

import com.nhnacademy.insightonruleengine.config.ActionPublisherProperties;
import com.nhnacademy.insightonruleengine.runner.application.action.ActionPublisher;
import com.nhnacademy.insightonruleengine.runner.model.action.AiSuggestionActionEvent;
import com.nhnacademy.insightonruleengine.runner.model.action.EngineAlertActionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

//액션 이벤트를 발행하는 RabbitMQ Publisher입니다.
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitActionPublisher implements ActionPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ActionPublisherProperties properties;

    //ALERT 액션 이벤트를 Topic Exchange 및 ai.alert.action 라우팅 키로 발행합니다.
    @Override
    public void publishAlert(EngineAlertActionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event는 필수입니다.");
        }
        try {
            rabbitTemplate.convertAndSend(
                    properties.exchange(),
                    properties.alertRoutingKey(),
                    event
            );
            log.debug("알림 액션 이벤트 발행 완료. eventId={}, flowId={}, locationId={}",
                    event.eventId(), event.flowId(), event.locationId());
        } catch (Exception exception) {
            throw new IllegalStateException("알림 액션 이벤트 발행에 실패했습니다.", exception);
        }
    }

    //AI_SUGGESTION 액션 이벤트를 Topic Exchange 및 ai.suggestion.action 라우팅 키로 발행합니다.
    @Override
    public void publishSuggestion(AiSuggestionActionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event는 필수입니다.");
        }
        try {
            rabbitTemplate.convertAndSend(
                    properties.exchange(),
                    properties.suggestionRoutingKey(),
                    event
            );
            log.debug("AI 제안 액션 이벤트 발행 완료. locationId={}, metricKey={}",
                    event.locationId(), event.metricKey());
        } catch (Exception exception) {
            throw new IllegalStateException("AI 제안 액션 이벤트 발행에 실패했습니다.", exception);
        }
    }
}
