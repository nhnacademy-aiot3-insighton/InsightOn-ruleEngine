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

    @RabbitListener(queues = "${rule-engine.sensor-event.queue}")
    public void consume(SensorEvent event) {
        flowRunner.run(event);
    }
}
