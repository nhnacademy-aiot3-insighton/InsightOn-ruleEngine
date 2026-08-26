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
        log.info("LOCATION_DELETED cleanup started. locationId={}", event.locationId());
        cleanupService.cleanupByLocation(event.locationId());
        log.info("LOCATION_DELETED cleanup completed. locationId={}", event.locationId());
    }
}
