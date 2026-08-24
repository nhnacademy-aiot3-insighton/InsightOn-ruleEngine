package com.nhnacademy.insightonruleengine.runner.infrastructure.inbound.rabbitmq;

import com.nhnacademy.insightonruleengine.flow.application.cleanup.GroupDeletionCleanupService;
import com.nhnacademy.insightonruleengine.runner.model.LocationDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rule-engine.location-deletion", name = "enabled", havingValue = "true")
public class LocationDeletionListener {

    private final GroupDeletionCleanupService cleanupService;

    @RabbitListener(
            queues = "${rule-engine.location-deletion.queue}",
            containerFactory = "locationDeletionListenerContainerFactory"
    )
    public void consume(LocationDeletedEvent event) {
        event.validate();
        log.info("LOCATION_DELETED cleanup started. locationId={}", event.locationId());
        cleanupService.cleanupLocation(event.locationId());
        log.info("LOCATION_DELETED cleanup completed. locationId={}", event.locationId());
    }
}
