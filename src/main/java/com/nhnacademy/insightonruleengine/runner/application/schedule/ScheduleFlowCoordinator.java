package com.nhnacademy.insightonruleengine.runner.application.schedule;

import com.nhnacademy.insightonruleengine.config.ScheduleExecutionProperties;
import com.nhnacademy.insightonruleengine.flow.application.assembly.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.exception.FlowNotActiveException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.FlowNotFoundException;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.trigger.ScheduleParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import com.nhnacademy.insightonruleengine.runner.application.FlowRunner;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.ScheduleExecutionLockRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
public class ScheduleFlowCoordinator {

    private final FlowRepository flowRepository;
    private final FlowDefinitionAssembler flowDefinitionAssembler;
    private final NodeParamsParser nodeParamsParser;
    private final FlowRunner flowRunner;
    private final ScheduleExecutionLockRepository executionLockRepository;
    private final ScheduleExecutionProperties properties;
    private final TaskScheduler taskScheduler;
    private final Map<Long, ScheduledFuture<?>> registrations = new ConcurrentHashMap<>();

    public ScheduleFlowCoordinator(
            FlowRepository flowRepository,
            FlowDefinitionAssembler flowDefinitionAssembler,
            NodeParamsParser nodeParamsParser,
            FlowRunner flowRunner,
            ScheduleExecutionLockRepository executionLockRepository,
            ScheduleExecutionProperties properties,
            @Qualifier("scheduleFlowTaskScheduler") TaskScheduler taskScheduler
    ) {
        this.flowRepository = flowRepository;
        this.flowDefinitionAssembler = flowDefinitionAssembler;
        this.nodeParamsParser = nodeParamsParser;
        this.flowRunner = flowRunner;
        this.executionLockRepository = executionLockRepository;
        this.properties = properties;
        this.taskScheduler = taskScheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void warmUp() {
        ReconciliationResult result = reconcileNow();
        log.info("ACTIVE Schedule Flow 등록을 완료했습니다. registeredCount={}", result.totalCount());
    }

    @Scheduled(
            fixedDelayString = "${rule-engine.schedule.reconciliation-interval:60000}",
            initialDelayString = "${rule-engine.schedule.reconciliation-interval:60000}"
    )
    @Transactional(readOnly = true)
    public void reconcileActiveSchedules() {
        ReconciliationResult result = reconcileNow();
        if (result.registeredCount() > 0 || result.cancelledCount() > 0) {
            log.debug(
                    "ACTIVE Schedule Flow 등록을 재조정했습니다. registeredCount={}, cancelledCount={}, totalCount={}",
                    result.registeredCount(),
                    result.cancelledCount(),
                    result.totalCount()
            );
        }
    }

    public void registerAfterCommit(Long groupId, Long flowId) {
        validateId(groupId, "groupId");
        validateId(flowId, "flowId");
        afterCommit(() -> registerActiveSafely(groupId, flowId));
    }

    public void cancelAfterCommit(Long flowId) {
        validateId(flowId, "flowId");
        afterCommit(() -> cancel(flowId));
    }

    public void cancelAll(Collection<Long> flowIds) {
        if (flowIds == null) {
            throw new IllegalArgumentException("flowIds는 필수입니다.");
        }
        flowIds.forEach(this::cancel);
    }

    private synchronized void registerActive(Long groupId, Long flowId) {
        FlowDefinition definition;
        try {
            definition = flowDefinitionAssembler.assembleActive(groupId, flowId);
        } catch (FlowNotFoundException | FlowNotActiveException exception) {
            cancel(flowId);
            return;
        }

        if (scheduleTrigger(definition).isEmpty()) {
            cancel(flowId);
            return;
        }
        register(definition);
    }

    synchronized void register(FlowDefinition definition) {
        if (definition == null || definition.status() != FlowStatus.ACTIVE) {
            throw new IllegalArgumentException("ACTIVE FlowDefinition이 필요합니다.");
        }
        NodeDefinition trigger = scheduleTrigger(definition)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Schedule Trigger Flow가 아닙니다. flowId=" + definition.flowId()));
        ScheduleParams params = nodeParamsParser.parse(NodeType.SCHEDULE, trigger.configuration());

        cancel(definition.flowId());
        ScheduledExecutionTrigger cronTrigger = new ScheduledExecutionTrigger(
                params.cron(),
                properties.zoneId()
        );
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> execute(definition.groupId(), definition.flowId(), cronTrigger.scheduledExecution()),
                cronTrigger
        );
        if (future == null) {
            throw new IllegalStateException(
                    "Schedule Flow를 등록할 수 없습니다. flowId=" + definition.flowId());
        }
        registrations.put(definition.flowId(), future);
        log.info("Schedule Flow를 등록했습니다. flowId={}, cron={}, zone={}",
                definition.flowId(), params.cron(), properties.zone());
    }

    public synchronized void cancel(Long flowId) {
        if (flowId == null) {
            return;
        }
        ScheduledFuture<?> future = registrations.remove(flowId);
        if (future != null) {
            future.cancel(false);
            log.info("Schedule Flow 등록을 취소했습니다. flowId={}", flowId);
        }
    }

    boolean isRegistered(Long flowId) {
        return registrations.containsKey(flowId);
    }

    private synchronized ReconciliationResult reconcileNow() {
        Collection<Flow> activeSchedules = flowRepository.findAllByStatusAndNodeType(
                FlowStatus.ACTIVE,
                NodeType.SCHEDULE
        );
        Set<Long> activeFlowIds = new HashSet<>();
        int registeredCount = 0;
        for (Flow flow : activeSchedules) {
            activeFlowIds.add(flow.getId());
            if (registrations.containsKey(flow.getId())) {
                continue;
            }
            registerActiveSafely(flow.getGroupId(), flow.getId());
            if (registrations.containsKey(flow.getId())) {
                registeredCount++;
            }
        }

        Collection<Long> staleFlowIds = registrations.keySet().stream()
                .filter(flowId -> !activeFlowIds.contains(flowId))
                .toList();
        staleFlowIds.forEach(this::cancel);
        return new ReconciliationResult(registeredCount, staleFlowIds.size(), registrations.size());
    }

    private void execute(Long groupId, Long flowId, Instant scheduledAt) {
        if (scheduledAt == null) {
            log.error("Schedule 실행 예정 시각을 확인할 수 없습니다. flowId={}", flowId);
            return;
        }
        try {
            FlowDefinition definition = flowDefinitionAssembler.assembleActive(groupId, flowId);
            if (scheduleTrigger(definition).isEmpty()) {
                cancel(flowId);
                return;
            }
            if (!executionLockRepository.acquire(flowId, scheduledAt)) {
                log.debug("다른 Rule Engine 인스턴스가 Schedule Flow를 실행합니다. flowId={}, scheduledAt={}",
                        flowId, scheduledAt);
                return;
            }
            flowRunner.runScheduled(definition, scheduledAt);
        } catch (FlowNotFoundException | FlowNotActiveException exception) {
            cancel(flowId);
            log.info("비활성 Schedule Flow 등록을 정리했습니다. flowId={}", flowId);
        } catch (RuntimeException exception) {
            log.error("Schedule Flow 실행 준비에 실패했습니다. flowId={}, scheduledAt={}",
                    flowId, scheduledAt, exception);
        }
    }

    private Optional<NodeDefinition> scheduleTrigger(FlowDefinition definition) {
        return definition.nodes().stream()
                .filter(node -> node.nodeType() == NodeType.SCHEDULE)
                .findFirst();
    }

    private void registerActiveSafely(Long groupId, Long flowId) {
        try {
            registerActive(groupId, flowId);
        } catch (RuntimeException exception) {
            log.error("Schedule Flow 등록에 실패했습니다. groupId={}, flowId={}",
                    groupId, flowId, exception);
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void validateId(Long id, String fieldName) {
        if (id == null || id <= 0L) {
            throw new IllegalArgumentException(fieldName + "는 양수여야 합니다.");
        }
    }

    private record ReconciliationResult(
            int registeredCount,
            int cancelledCount,
            int totalCount
    ) {
    }
}
