package com.nhnacademy.insightonruleengine.runner.application.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.SimpleTriggerContext;

class ScheduledExecutionTriggerTest {

    @Test
    void exposesThePlannedExecutionTimeInTheConfiguredZone() {
        ScheduledExecutionTrigger trigger = new ScheduledExecutionTrigger(
                "0 0 10 * * *",
                ZoneId.of("Asia/Seoul")
        );
        SimpleTriggerContext context = new SimpleTriggerContext(Clock.fixed(
                Instant.parse("2026-08-24T00:30:00Z"),
                ZoneOffset.UTC
        ));

        Instant nextExecution = trigger.nextExecution(context);

        assertEquals(Instant.parse("2026-08-24T01:00:00Z"), nextExecution);
        assertEquals(nextExecution, trigger.scheduledExecution());
    }

    @Test
    void skipsOccurrencesMissedWhileThePreviousExecutionWasRunning() {
        ScheduledExecutionTrigger trigger = new ScheduledExecutionTrigger(
                "0 0 * * * *",
                ZoneOffset.UTC
        );
        SimpleTriggerContext context = new SimpleTriggerContext(Clock.fixed(
                Instant.parse("2026-08-24T03:00:00Z"),
                ZoneOffset.UTC
        ));
        context.update(
                Instant.parse("2026-08-24T01:00:00Z"),
                Instant.parse("2026-08-24T01:00:00Z"),
                Instant.parse("2026-08-24T02:05:00Z")
        );

        Instant nextExecution = trigger.nextExecution(context);

        assertEquals(Instant.parse("2026-08-24T03:00:00Z"), nextExecution);
        assertEquals(nextExecution, trigger.scheduledExecution());
    }
}
