package com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq;

import com.nhnacademy.insightonruleengine.flow.application.cleanup.FlowCleanupService;
import com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq.dto.LocationDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "rule-engine.core-lifecycle-events",
        name = "enabled",
        havingValue = "true"
)
public class LocationDeletedEventListener {

    private final FlowCleanupService cleanupService;

    @RabbitListener(
            queues = "${rule-engine.core-lifecycle-events.location-deleted.queue}",
            containerFactory = "locationDeletedEventListenerContainerFactory"
    )
    public void consume(LocationDeletedEvent event) {
        event.validate();
        cleanupService.cleanupByLocation(event.locationId());
        log.info("장소 삭제에 따른 플로우 정리 완료. locationId={}", event.locationId());
    }
}
