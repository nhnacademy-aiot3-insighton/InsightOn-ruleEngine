package com.nhnacademy.insightonruleengine.runner.messaging;

import com.nhnacademy.insightonruleengine.runner.FlowRunner;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;


/**
 *
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rule-engine.rabbitmq.enabled", havingValue = "true")
public class SensorEventConsumer {

    private final FlowRunner flowRunner;

    // 실시간 우선 정책에 따라 broker ACK 후 처리하며 실패 메시지를 재전달하지 않습니다.
    @RabbitListener(queues = "${rule-engine.sensor-event.queue}", ackMode = "NONE")
    public void consume(SensorEvent event) {
        flowRunner.run(event);
    }
}
