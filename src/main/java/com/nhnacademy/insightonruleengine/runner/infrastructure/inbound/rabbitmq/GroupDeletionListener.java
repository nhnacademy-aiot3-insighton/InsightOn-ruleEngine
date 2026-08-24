package com.nhnacademy.insightonruleengine.runner.infrastructure.inbound.rabbitmq;

import com.nhnacademy.insightonruleengine.flow.application.cleanup.GroupDeletionCleanupService;
import com.nhnacademy.insightonruleengine.runner.model.GroupDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rule-engine.group-deletion", name = "enabled", havingValue = "true")
public class GroupDeletionListener {

    private final GroupDeletionCleanupService cleanupService;

    @RabbitListener(
            queues = "${rule-engine.group-deletion.queue}",
            containerFactory = "groupDeletionListenerContainerFactory"
    )
    public void consume(GroupDeletedEvent event) {
        event.validate();
        log.info("GROUP_DELETED cleanup started. groupId={}, locationIds={}",
                event.groupId(), event.locationIds());
        cleanupService.cleanup(event.groupId(), event.locationIds());
        log.info("GROUP_DELETED cleanup completed. groupId={}", event.groupId());
    }
}
