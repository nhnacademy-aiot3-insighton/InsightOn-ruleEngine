package com.nhnacademy.insightonruleengine.runner.application.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.support.CronTrigger;

final class ScheduledExecutionTrigger implements Trigger {

    private final CronTrigger delegate;
    private final AtomicReference<Instant> scheduledExecution = new AtomicReference<>();

    ScheduledExecutionTrigger(String expression, ZoneId zoneId) {
        delegate = new CronTrigger(expression, zoneId);
    }

    @Override
    public Instant nextExecution(TriggerContext triggerContext) {
        Instant nextExecution = delegate.nextExecution(triggerContext);
        scheduledExecution.set(nextExecution);
        return nextExecution;
    }

    Instant scheduledExecution() {
        return scheduledExecution.get();
    }
}
