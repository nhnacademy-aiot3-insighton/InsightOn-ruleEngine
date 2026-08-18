package com.nhnacademy.insightonruleengine.runner.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.runner.FlowRunner;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.dto.TelemetryEventMessage;
import com.nhnacademy.insightonruleengine.runner.redis.FlowRouteRedisRepository;
import com.rabbitmq.client.Channel;
import java.util.Set;
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
    private final FlowRouteRedisRepository flowRouteRedisRepository;
    private final FlowRunner flowRunner;

    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            byte[] body = message.getBody();
            if (body == null || body.length == 0) {
                log.warn("Telemetry message body is empty. Discarding message. deliveryTag={}", deliveryTag);
                channel.basicAck(deliveryTag, false);
                return;
            }

            TelemetryEventMessage eventMessage;
            try {
                eventMessage = objectMapper.readValue(body, TelemetryEventMessage.class);
            } catch (Exception exception) {
                log.warn("Failed to deserialize Telemetry message. Discarding message. deliveryTag={}",
                        deliveryTag, exception);
                channel.basicAck(deliveryTag, false);
                return;
            }

            try {
                eventMessage.validate();
            } catch (IllegalArgumentException exception) {
                log.warn("Invalid Telemetry message payload: {}. Discarding message. deliveryTag={}",
                        exception.getMessage(), deliveryTag);
                channel.basicAck(deliveryTag, false);
                return;
            }

            Set<Long> activeFlowIds = flowRouteRedisRepository.findFlowIds(
                    eventMessage.groupId(),
                    eventMessage.locationId()
            );
            if (activeFlowIds.isEmpty()) {
                log.debug("No active flows found for groupId={}, locationId={}. Normal completion. deliveryTag={}",
                        eventMessage.groupId(), eventMessage.locationId(), deliveryTag);
                channel.basicAck(deliveryTag, false);
                return;
            }

            SensorEvent sensorEvent = eventMessage.toSensorEvent(objectMapper);
            flowRunner.run(sensorEvent);

            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            log.error(
                    "Unexpected error occurred while processing Telemetry message. Discarding message. deliveryTag={}",
                    deliveryTag, exception);
            channel.basicAck(deliveryTag, false);
        }
    }
}
