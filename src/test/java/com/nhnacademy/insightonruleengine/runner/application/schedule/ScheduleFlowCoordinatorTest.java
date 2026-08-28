package com.nhnacademy.insightonruleengine.runner.application.schedule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.ScheduleExecutionLockRepository;
import jakarta.validation.Validation;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
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

@ExtendWith(MockitoExtension.class)
class ScheduleFlowCoordinatorTest {

    @Mock
    private FlowRepository flowRepository;
    @Mock
    private FlowDefinitionAssembler flowDefinitionAssembler;
    @Mock
    private FlowRunner flowRunner;
    @Mock
    private ScheduleExecutionLockRepository executionLockRepository;
    @Mock
    private TaskScheduler taskScheduler;

    private ScheduledFuture<?> future;
    private ScheduleFlowCoordinator coordinator;

    @BeforeEach
    void setUp() {
        future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        coordinator = new ScheduleFlowCoordinator(
                flowRepository,
                flowDefinitionAssembler,
                new NodeParamsParser(
                        new ObjectMapper(),
                        Validation.buildDefaultValidatorFactory().getValidator()
                ),
                flowRunner,
                executionLockRepository,
                new ScheduleExecutionProperties("Asia/Seoul", Duration.ofMinutes(10), 2),
                taskScheduler
        );
    }

    @Test
    void registeredCronExecutesOnlyAfterDistributedLockAcquisition() {
        FlowDefinition definition = scheduleFlow(FlowStatus.ACTIVE);
        when(flowDefinitionAssembler.assembleActive(1L, 10L)).thenReturn(definition);

        coordinator.register(definition);

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(taskCaptor.capture(), triggerCaptor.capture());
        Instant scheduledAt = triggerCaptor.getValue().nextExecution(new SimpleTriggerContext());
        assertNotNull(scheduledAt);
        when(executionLockRepository.acquire(10L, scheduledAt)).thenReturn(true);

        taskCaptor.getValue().run();

        assertTrue(coordinator.isRegistered(10L));
        verify(flowRunner).runScheduled(definition, scheduledAt);
    }

    @Test
    void duplicateEngineSkipsTheSameScheduledOccurrence() {
        FlowDefinition definition = scheduleFlow(FlowStatus.ACTIVE);
        when(flowDefinitionAssembler.assembleActive(1L, 10L)).thenReturn(definition);
        coordinator.register(definition);

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(taskCaptor.capture(), triggerCaptor.capture());
        Instant scheduledAt = triggerCaptor.getValue().nextExecution(new SimpleTriggerContext());
        when(executionLockRepository.acquire(10L, scheduledAt)).thenReturn(false);

        taskCaptor.getValue().run();

        verify(flowRunner, never()).runScheduled(any(), any());
    }

    @Test
    void cancellationAfterCommitDoesNotReassembleFlowDefinition() {
        FlowDefinition active = scheduleFlow(FlowStatus.ACTIVE);
        coordinator.register(active);

        coordinator.cancelAfterCommit(10L);

        assertFalse(coordinator.isRegistered(10L));
        verify(future).cancel(false);
        verify(flowDefinitionAssembler, never()).assembleActive(any(), any());
    }

    @Test
    void activeFlowRegistrationAssemblesOnlyActiveDefinition() {
        FlowDefinition definition = scheduleFlow(FlowStatus.ACTIVE);
        when(flowDefinitionAssembler.assembleActive(1L, 10L)).thenReturn(definition);

        coordinator.registerAfterCommit(1L, 10L);

        assertTrue(coordinator.isRegistered(10L));
        verify(flowDefinitionAssembler).assembleActive(1L, 10L);
    }

    @Test
    void applicationReadyRestoresActiveScheduleFlows() {
        Flow flow = new Flow(1L, 20L, "정기 실행", null, FlowStatus.ACTIVE);
        ReflectionTestUtils.setField(flow, "id", 10L);
        FlowDefinition definition = scheduleFlow(FlowStatus.ACTIVE);
        when(flowRepository.findAllByStatusAndNodeType(FlowStatus.ACTIVE, NodeType.SCHEDULE))
                .thenReturn(List.of(flow));
        when(flowDefinitionAssembler.assembleActive(1L, 10L)).thenReturn(definition);

        coordinator.warmUp();

        assertTrue(coordinator.isRegistered(10L));
        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        verify(flowRepository).findAllByStatusAndNodeType(FlowStatus.ACTIVE, NodeType.SCHEDULE);
    }

    @Test
    void reconciliationRegistersNewSchedulesAndCancelsStaleSchedules() {
        coordinator.register(scheduleFlow(10L, FlowStatus.ACTIVE));
        Flow newSchedule = new Flow(1L, 20L, "새 정기 실행", null, FlowStatus.ACTIVE);
        ReflectionTestUtils.setField(newSchedule, "id", 20L);
        FlowDefinition newDefinition = scheduleFlow(20L, FlowStatus.ACTIVE);
        when(flowRepository.findAllByStatusAndNodeType(FlowStatus.ACTIVE, NodeType.SCHEDULE))
                .thenReturn(List.of(newSchedule));
        when(flowDefinitionAssembler.assembleActive(1L, 20L)).thenReturn(newDefinition);

        coordinator.reconcileActiveSchedules();

        assertFalse(coordinator.isRegistered(10L));
        assertTrue(coordinator.isRegistered(20L));
        verify(future).cancel(false);
        verify(flowDefinitionAssembler).assembleActive(1L, 20L);
    }

    @Test
    void reconciliationKeepsExistingRegistrationWithoutReassembly() {
        coordinator.register(scheduleFlow(10L, FlowStatus.ACTIVE));
        Flow existingSchedule = new Flow(1L, 20L, "정기 실행", null, FlowStatus.ACTIVE);
        ReflectionTestUtils.setField(existingSchedule, "id", 10L);
        when(flowRepository.findAllByStatusAndNodeType(FlowStatus.ACTIVE, NodeType.SCHEDULE))
                .thenReturn(List.of(existingSchedule));

        coordinator.reconcileActiveSchedules();

        assertTrue(coordinator.isRegistered(10L));
        verifyNoInteractions(flowDefinitionAssembler);
        verify(future, never()).cancel(false);
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
