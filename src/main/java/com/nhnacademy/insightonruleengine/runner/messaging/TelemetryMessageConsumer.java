package com.nhnacademy.insightonruleengine.runner.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.runner.dto.TelemetryEventMessage;
import com.nhnacademy.insightonruleengine.runner.orchestrator.TelemetryExecutionOrchestrator;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.stereotype.Component;

//16개 Telemetry Queue로부터 수신한 메시지를 검증하고 활성 Flow가 존재할 때만 실행하며 수동 ACK 처리합니다.
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryMessageConsumer implements ChannelAwareMessageListener {

    private final ObjectMapper objectMapper;
    private final TelemetryExecutionOrchestrator telemetryExecutionOrchestrator;

    // RabbitMQ 큐로부터 메시지를 수신하여 검증 후 오케스트레이터를 실행하고 항상 수동 ACK합니다.
    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            byte[] body = message.getBody();
            if (body == null || body.length == 0) {
                log.warn("Telemetry message body is empty. Discarding message. deliveryTag={}", deliveryTag);
                return;
            }

            TelemetryEventMessage eventMessage;
            try {
                eventMessage = objectMapper.readValue(body, TelemetryEventMessage.class);
            } catch (Exception exception) {
                log.warn("Failed to deserialize Telemetry message. Discarding message. deliveryTag={}",
                        deliveryTag, exception);
                return;
            }

            try {
                eventMessage.validate();
            } catch (IllegalArgumentException exception) {
                log.warn("Invalid Telemetry message payload: {}. Discarding message. deliveryTag={}",
                        exception.getMessage(), deliveryTag);
                return;
            }

            telemetryExecutionOrchestrator.orchestrate(eventMessage);

        } catch (Exception exception) {
            log.error(
                    "Unexpected error occurred while processing Telemetry message. Discarding message. deliveryTag={}",
                    deliveryTag, exception);
        } finally {
            channel.basicAck(deliveryTag, false);
        }
    }
}
