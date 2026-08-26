package com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq;

import com.nhnacademy.insightonruleengine.flow.application.cleanup.FlowCleanupService;
import com.nhnacademy.insightonruleengine.flow.infrastructure.inbound.rabbitmq.dto.GroupDeletedEvent;
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
public class GroupDeletedEventListener {

    private final FlowCleanupService cleanupService;

    @RabbitListener(
            queues = "${rule-engine.core-lifecycle-events.group-deleted.queue}",
            containerFactory = "groupDeletedEventListenerContainerFactory"
    )
    public void consume(GroupDeletedEvent event) {
        event.validate();
        log.info("GROUP_DELETED cleanup started. groupId={}, locationIds={}",
                event.groupId(), event.locationIds());
        cleanupService.cleanupByGroup(event.groupId(), event.locationIds());
        log.info("GROUP_DELETED cleanup completed. groupId={}", event.groupId());
    }
}
