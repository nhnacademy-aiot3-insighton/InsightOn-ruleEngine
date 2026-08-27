package com.nhnacademy.insightonruleengine.runner.infrastructure.inbound.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.runner.application.telemetry.TelemetryExecutionOrchestrator;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
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

    private static final int MAX_MESSAGE_BYTES = 256 * 1024;

    private final ObjectMapper objectMapper;
    private final TelemetryExecutionOrchestrator telemetryExecutionOrchestrator;

    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        byte[] body = message.getBody();
        if (body == null || body.length == 0 || body.length > MAX_MESSAGE_BYTES) {
            log.warn("Telemetry 메시지 크기가 올바르지 않아 폐기합니다. deliveryTag={}, size={}",
                    deliveryTag, body == null ? 0 : body.length);
            channel.basicAck(deliveryTag, false);
            return;
        }

        SensorEvent sensorEvent = parseAndValidate(body, deliveryTag);
        if (sensorEvent == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            telemetryExecutionOrchestrator.orchestrate(sensorEvent);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            log.error(
                    "Telemetry 메시지 처리에 실패하여 폐기합니다. deliveryTag={}",
                    deliveryTag, exception);
            channel.basicAck(deliveryTag, false);
        }
    }

    private SensorEvent parseAndValidate(byte[] body, long deliveryTag) {
        try {
            return objectMapper.readValue(body, SensorEvent.class);
        } catch (IllegalArgumentException exception) {
            log.warn("Telemetry 메시지 내용이 올바르지 않아 폐기합니다. message={}, deliveryTag={}",
                    exception.getMessage(), deliveryTag);
            return null;
        } catch (Exception exception) {
            log.warn("Telemetry 메시지 변환에 실패하여 폐기합니다. deliveryTag={}, errorType={}",
                    deliveryTag, exception.getClass().getSimpleName());
            log.debug("Telemetry 메시지 변환 실패 상세. deliveryTag={}", deliveryTag, exception);
            return null;
        }
    }
}
