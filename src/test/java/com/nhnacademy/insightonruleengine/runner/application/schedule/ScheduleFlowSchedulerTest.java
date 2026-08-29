package com.nhnacademy.insightonruleengine.runner.application.schedule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.config.ScheduleExecutionProperties;
import com.nhnacademy.insightonruleengine.flow.application.assembly.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import com.nhnacademy.insightonruleengine.runner.application.FlowRunner;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.ScheduleExecutionRedisRepository;
import jakarta.validation.Validation;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class ScheduleFlowSchedulerTest {

    @Mock
    private FlowRepository flowRepository;
    @Mock
    private FlowDefinitionAssembler flowDefinitionAssembler;
    @Mock
    private FlowRunner flowRunner;
    @Mock
    private ScheduleExecutionRedisRepository executionRedisRepository;
    @Mock
    private TaskScheduler taskScheduler;

    private ScheduledFuture<?> future;
    private ScheduleFlowScheduler scheduler;
    private final Logger logger = (Logger) LoggerFactory.getLogger(ScheduleFlowScheduler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;
    private boolean originalAdditive;

    @BeforeEach
    void setUp() {
        originalLevel = logger.getLevel();
        originalAdditive = logger.isAdditive();
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        appender.start();
        logger.addAppender(appender);
        future = mock(ScheduledFuture.class);
        lenient().doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        scheduler = new ScheduleFlowScheduler(
                flowRepository,
                flowDefinitionAssembler,
                new NodeParamsParser(
                        new ObjectMapper(),
                        Validation.buildDefaultValidatorFactory().getValidator()
                ),
                flowRunner,
                executionRedisRepository,
                new ScheduleExecutionProperties("Asia/Seoul", Duration.ofMinutes(10), 2),
                taskScheduler
        );
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(originalLevel);
        logger.setAdditive(originalAdditive);
    }

    @Test
    void registeredCronExecutesOnlyAfterDistributedClaim() {
        FlowDefinition definition = scheduleFlow(FlowStatus.ACTIVE);
        scheduler.register(definition);

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(taskCaptor.capture(), triggerCaptor.capture());
        Instant scheduledAt = triggerCaptor.getValue().nextExecution(new SimpleTriggerContext());
        assertNotNull(scheduledAt);
        when(executionRedisRepository.claimIfActive(10L, scheduledAt)).thenReturn(true);

        taskCaptor.getValue().run();

        assertTrue(scheduler.isRegistered(10L));
        verify(flowRunner).runScheduled(definition, scheduledAt);
        verifyNoInteractions(flowDefinitionAssembler);
    }

    @Test
    void duplicateEngineSkipsTheSameScheduledOccurrence() {
        FlowDefinition definition = scheduleFlow(FlowStatus.ACTIVE);
        scheduler.register(definition);

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(taskCaptor.capture(), triggerCaptor.capture());
        Instant scheduledAt = triggerCaptor.getValue().nextExecution(new SimpleTriggerContext());
        when(executionRedisRepository.claimIfActive(10L, scheduledAt)).thenReturn(false);

        taskCaptor.getValue().run();

        verify(flowRunner, never()).runScheduled(any(), any());
    }

    @Test
    void cancellationAfterCommitDoesNotReassembleFlowDefinition() {
        scheduler.register(scheduleFlow(FlowStatus.ACTIVE));

        scheduler.cancelAfterCommit(10L);

        assertFalse(scheduler.isRegistered(10L));
        verify(future).cancel(false);
        verify(executionRedisRepository).markInactive(10L);
        verify(flowDefinitionAssembler, never()).assembleActive(any(), any());
    }

    @Test
    void cancellationWithoutLocalScheduleOnlyUpdatesExistingRedisState() {
        scheduler.cancelAfterCommit(10L);

        verify(executionRedisRepository).markInactiveIfPresent(10L);
        verify(executionRedisRepository, never()).markInactive(10L);
    }

    @Test
    void activeFlowRegistrationPublishesStateAfterDefinitionValidation() {
        FlowDefinition definition = scheduleFlow(FlowStatus.ACTIVE);
        when(flowDefinitionAssembler.assembleActive(1L, 10L)).thenReturn(definition);

        scheduler.registerAfterCommit(1L, 10L);

        assertTrue(scheduler.isRegistered(10L));
        verify(flowDefinitionAssembler).assembleActive(1L, 10L);
        verify(executionRedisRepository).markActive(10L);
    }

    @Test
    void applicationReadyRestoresActiveScheduleFlowsAndMissingRedisState() {
        Flow flow = activeFlow(10L, "정기 실행");
        FlowDefinition definition = scheduleFlow(FlowStatus.ACTIVE);
        when(executionRedisRepository.beginReconciliation()).thenReturn(5L);
        when(executionRedisRepository.repairActive(10L, 5L)).thenReturn(true);
        when(flowRepository.findAllByStatusAndNodeType(FlowStatus.ACTIVE, NodeType.SCHEDULE))
                .thenReturn(List.of(flow));
        when(flowDefinitionAssembler.assembleActive(1L, 10L)).thenReturn(definition);

        scheduler.warmUp();

        assertTrue(scheduler.isRegistered(10L));
        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        verify(executionRedisRepository).repairActive(10L, 5L);
    }

    @Test
    void reconciliationRegistersNewSchedulesAndCancelsStaleSchedules() {
        scheduler.register(scheduleFlow(10L, FlowStatus.ACTIVE));
        Flow newSchedule = activeFlow(20L, "새 정기 실행");
        FlowDefinition newDefinition = scheduleFlow(20L, FlowStatus.ACTIVE);
        when(executionRedisRepository.beginReconciliation()).thenReturn(6L);
        when(flowRepository.findAllByStatusAndNodeType(FlowStatus.ACTIVE, NodeType.SCHEDULE))
                .thenReturn(List.of(newSchedule));
        when(flowDefinitionAssembler.assembleActive(1L, 20L)).thenReturn(newDefinition);

        scheduler.reconcileActiveSchedules();

        assertFalse(scheduler.isRegistered(10L));
        assertTrue(scheduler.isRegistered(20L));
        verify(future).cancel(false);
        verify(executionRedisRepository).repairActive(20L, 6L);
        verify(executionRedisRepository).markInactive(10L);
    }

    @Test
    void reconciliationKeepsExistingRegistrationWithoutReassemblyOrStateRewrite() {
        scheduler.register(scheduleFlow(10L, FlowStatus.ACTIVE));
        Flow existingSchedule = activeFlow(10L, "정기 실행");
        when(executionRedisRepository.beginReconciliation()).thenReturn(7L);
        when(flowRepository.findAllByStatusAndNodeType(FlowStatus.ACTIVE, NodeType.SCHEDULE))
                .thenReturn(List.of(existingSchedule));

        scheduler.reconcileActiveSchedules();

        assertTrue(scheduler.isRegistered(10L));
        verify(executionRedisRepository).repairActive(10L, 7L);
        verify(executionRedisRepository, never()).markActive(10L);
        verifyNoInteractions(flowDefinitionAssembler);
        verify(future, never()).cancel(false);
    }

    @Test
    void reconciliationStopsRedisRepairAfterFirstFailureButKeepsLocalRegistration() {
        Flow first = activeFlow(10L, "첫 번째");
        Flow second = activeFlow(20L, "두 번째");
        when(executionRedisRepository.beginReconciliation()).thenReturn(8L);
        when(executionRedisRepository.repairActive(10L, 8L))
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(flowRepository.findAllByStatusAndNodeType(FlowStatus.ACTIVE, NodeType.SCHEDULE))
                .thenReturn(List.of(first, second));
        when(flowDefinitionAssembler.assembleActive(1L, 10L))
                .thenReturn(scheduleFlow(10L, FlowStatus.ACTIVE));
        when(flowDefinitionAssembler.assembleActive(1L, 20L))
                .thenReturn(scheduleFlow(20L, FlowStatus.ACTIVE));

        scheduler.reconcileActiveSchedules();

        assertTrue(scheduler.isRegistered(10L));
        assertTrue(scheduler.isRegistered(20L));
        verify(executionRedisRepository, never()).repairActive(20L, 8L);
    }

    @Test
    void repeatedReconciliationFailureIsSuppressedUntilRecovery() {
        RuntimeException exception = new IllegalStateException("DB unavailable");
        when(executionRedisRepository.beginReconciliation()).thenReturn(9L);
        when(flowRepository.findAllByStatusAndNodeType(FlowStatus.ACTIVE, NodeType.SCHEDULE))
                .thenThrow(exception)
                .thenThrow(exception)
                .thenReturn(List.of());

        scheduler.reconcileActiveSchedules();
        scheduler.reconcileActiveSchedules();
        scheduler.reconcileActiveSchedules();

        assertEquals(1L, count(Level.WARN, "활성 스케줄 플로우 재조정에 실패했습니다."));
        assertEquals(1L, count(Level.INFO, "활성 스케줄 플로우 재조정이 복구됐습니다."));
        assertTrue(formattedMessages().stream()
                .anyMatch(message -> message.contains("suppressedFailureCount=1")));
    }

    @Test
    void repeatedRegistrationFailureIsSuppressedUntilRecovery() {
        RuntimeException exception = new IllegalStateException("invalid schedule definition");
        FlowDefinition definition = scheduleFlow(FlowStatus.ACTIVE);
        when(flowDefinitionAssembler.assembleActive(1L, 10L))
                .thenThrow(exception)
                .thenThrow(exception)
                .thenReturn(definition);

        scheduler.registerAfterCommit(1L, 10L);
        scheduler.registerAfterCommit(1L, 10L);
        scheduler.registerAfterCommit(1L, 10L);

        assertEquals(1L, count(Level.ERROR, "스케줄 플로우 등록에 실패했습니다."));
        assertEquals(1L, count(Level.INFO, "스케줄 플로우 등록 처리가 복구됐습니다."));
        assertTrue(formattedMessages().stream()
                .anyMatch(message -> message.contains("suppressedFailureCount=1")));
    }

    @Test
    void repeatedRedisFailureLogsContextOnceAndReportsRecovery() {
        FlowDefinition definition = scheduleFlow(FlowStatus.ACTIVE);
        scheduler.register(definition);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(taskCaptor.capture(), triggerCaptor.capture());
        Instant scheduledAt = triggerCaptor.getValue().nextExecution(new SimpleTriggerContext());
        RuntimeException exception = new IllegalStateException("Redis unavailable");
        when(executionRedisRepository.claimIfActive(10L, scheduledAt))
                .thenThrow(exception)
                .thenThrow(exception)
                .thenReturn(false);

        taskCaptor.getValue().run();
        taskCaptor.getValue().run();
        taskCaptor.getValue().run();

        assertEquals(1L, count(Level.ERROR, "스케줄 실행용 Redis에 접근할 수 없습니다."));
        assertEquals(1L, count(Level.INFO, "스케줄 실행용 Redis 접근이 복구됐습니다."));
        assertTrue(formattedMessages().stream().anyMatch(message ->
                message.contains("flowId=10")
                        && message.contains("scheduledAt=" + scheduledAt)));
        assertTrue(formattedMessages().stream()
                .anyMatch(message -> message.contains("suppressedFailureCount=1")));
    }

    @Test
    void flowRunnerFailureIsNotReportedAsRedisFailure() {
        FlowDefinition definition = scheduleFlow(FlowStatus.ACTIVE);
        scheduler.register(definition);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(taskCaptor.capture(), triggerCaptor.capture());
        Instant scheduledAt = triggerCaptor.getValue().nextExecution(new SimpleTriggerContext());
        when(executionRedisRepository.claimIfActive(10L, scheduledAt)).thenReturn(true);
        doThrow(new IllegalStateException("runner unavailable"))
                .when(flowRunner).runScheduled(definition, scheduledAt);

        taskCaptor.getValue().run();

        assertEquals(1L, count(Level.ERROR, "스케줄 플로우 실행 위임에 실패했습니다."));
        assertEquals(0L, count(Level.ERROR, "스케줄 실행용 Redis에 접근할 수 없습니다."));
    }

    private long count(Level level, String prefix) {
        return appender.list.stream()
                .filter(event -> event.getLevel() == level)
                .filter(event -> event.getFormattedMessage().startsWith(prefix))
                .count();
    }

    private List<String> formattedMessages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private Flow activeFlow(Long flowId, String name) {
        Flow flow = new Flow(1L, 20L, name, null, FlowStatus.ACTIVE);
        ReflectionTestUtils.setField(flow, "id", flowId);
        return flow;
    }

    private FlowDefinition scheduleFlow(FlowStatus status) {
        return scheduleFlow(10L, status);
    }

    private FlowDefinition scheduleFlow(Long flowId, FlowStatus status) {
        return new FlowDefinition(
                flowId,
                1L,
                20L,
                "정기 실행",
                null,
                status,
                OffsetDateTime.parse("2026-08-24T00:00:00Z"),
                List.of(new NodeDefinition(
                        flowId * 10,
                        NodeType.SCHEDULE,
                        JsonNodeFactory.instance.objectNode().put("cron", "0 0 * * * *")
                )),
                List.of()
        );
    }
}
