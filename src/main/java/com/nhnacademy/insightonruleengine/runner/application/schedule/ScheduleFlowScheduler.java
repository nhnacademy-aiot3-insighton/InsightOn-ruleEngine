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
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.ScheduleExecutionRedisRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
public class ScheduleFlowScheduler {

    private final FlowRepository flowRepository;
    private final FlowDefinitionAssembler flowDefinitionAssembler;
    private final NodeParamsParser nodeParamsParser;
    private final FlowRunner flowRunner;
    private final ScheduleExecutionRedisRepository executionRedisRepository;
    private final ScheduleExecutionProperties properties;
    private final TaskScheduler taskScheduler;
    private final Map<Long, ScheduledFuture<?>> registrations = new ConcurrentHashMap<>();
    private final AtomicBoolean redisUnavailable = new AtomicBoolean();

    public ScheduleFlowScheduler(
            FlowRepository flowRepository,
            FlowDefinitionAssembler flowDefinitionAssembler,
            NodeParamsParser nodeParamsParser,
            FlowRunner flowRunner,
            ScheduleExecutionRedisRepository executionRedisRepository,
            ScheduleExecutionProperties properties,
            @Qualifier("scheduleFlowTaskScheduler") TaskScheduler taskScheduler
    ) {
        this.flowRepository = flowRepository;
        this.flowDefinitionAssembler = flowDefinitionAssembler;
        this.nodeParamsParser = nodeParamsParser;
        this.flowRunner = flowRunner;
        this.executionRedisRepository = executionRedisRepository;
        this.properties = properties;
        this.taskScheduler = taskScheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void warmUp() {
        ReconciliationResult result = reconcileNow();
        log.info("활성 스케줄 플로우 등록을 완료했습니다. registeredCount={}", result.totalCount());
    }

    @Scheduled(
            fixedDelayString = "${rule-engine.schedule.reconciliation-interval:60000}",
            initialDelayString = "${rule-engine.schedule.reconciliation-interval:60000}"
    )
    @Transactional(readOnly = true)
    public void reconcileActiveSchedules() {
        ReconciliationResult result = reconcileNow();
        if (result.registeredCount() > 0
                || result.cancelledCount() > 0
                || result.repairedStateCount() > 0) {
            log.debug(
                    "활성 스케줄 플로우 등록을 재조정했습니다. registeredCount={}, cancelledCount={}, "
                            + "repairedStateCount={}, totalCount={}",
                    result.registeredCount(),
                    result.cancelledCount(),
                    result.repairedStateCount(),
                    result.totalCount()
            );
        }
    }

    public void registerAfterCommit(Long groupId, Long flowId) {
        validateId(groupId, "groupId");
        validateId(flowId, "flowId");
        afterCommit(() -> registerActiveSafely(groupId, flowId, true));
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

    private synchronized void registerActive(Long groupId, Long flowId, boolean publishActiveState) {
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
        if (publishActiveState) {
            markActiveSafely(flowId);
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

        cancelLocalRegistration(definition.flowId());
        ScheduledExecutionTrigger cronTrigger = new ScheduledExecutionTrigger(
                params.cron(),
                properties.zoneId()
        );
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> execute(definition, cronTrigger.scheduledExecution()),
                cronTrigger
        );
        if (future == null) {
            throw new IllegalStateException(
                    "Schedule Flow를 등록할 수 없습니다. flowId=" + definition.flowId());
        }
        registrations.put(definition.flowId(), future);
        log.debug("스케줄 플로우를 등록했습니다. flowId={}, cron={}, zone={}",
                definition.flowId(), params.cron(), properties.zone());
    }

    public synchronized void cancel(Long flowId) {
        if (flowId == null) {
            return;
        }
        boolean registered = cancelLocalRegistration(flowId);
        markInactiveSafely(flowId, registered);
    }

    private boolean cancelLocalRegistration(Long flowId) {
        ScheduledFuture<?> future = registrations.remove(flowId);
        if (future == null) {
            return false;
        }
        future.cancel(false);
        log.debug("스케줄 플로우 등록을 취소했습니다. flowId={}", flowId);
        return true;
    }

    boolean isRegistered(Long flowId) {
        return registrations.containsKey(flowId);
    }

    private synchronized ReconciliationResult reconcileNow() {
        // DB 조회 뒤 발생한 활성/비활성 변경이 오래된 조회 결과로 덮이지 않도록 먼저 기준 버전을 확보합니다.
        Long reconciliationVersion = beginReconciliationSafely();
        Collection<Flow> activeSchedules = flowRepository.findAllByStatusAndNodeType(
                FlowStatus.ACTIVE,
                NodeType.SCHEDULE
        );
        Set<Long> activeFlowIds = new HashSet<>();
        int registeredCount = 0;
        int repairedStateCount = 0;
        boolean canRepairRedisState = reconciliationVersion != null;

        for (Flow flow : activeSchedules) {
            activeFlowIds.add(flow.getId());
            if (canRepairRedisState) {
                try {
                    if (executionRedisRepository.repairActive(flow.getId(), reconciliationVersion)) {
                        repairedStateCount++;
                    }
                } catch (RuntimeException exception) {
                    canRepairRedisState = false;
                    reportRedisFailure("활성 상태 재조정", exception);
                }
            }
            if (registrations.containsKey(flow.getId())) {
                continue;
            }
            registerActiveSafely(flow.getGroupId(), flow.getId(), false);
            if (registrations.containsKey(flow.getId())) {
                registeredCount++;
            }
        }

        Collection<Long> staleFlowIds = registrations.keySet().stream()
                .filter(flowId -> !activeFlowIds.contains(flowId))
                .toList();
        staleFlowIds.forEach(this::cancel);
        if (reconciliationVersion != null && canRepairRedisState) {
            reportRedisRecovery();
        }
        return new ReconciliationResult(
                registeredCount,
                staleFlowIds.size(),
                repairedStateCount,
                registrations.size()
        );
    }

    private void execute(FlowDefinition definition, Instant scheduledAt) {
        Long flowId = definition.flowId();
        if (scheduledAt == null) {
            log.error("스케줄 실행 예정 시각을 확인할 수 없습니다. flowId={}", flowId);
            return;
        }
        try {
            if (!executionRedisRepository.claimIfActive(flowId, scheduledAt)) {
                reportRedisRecovery();
                log.debug("비활성 상태이거나 다른 Rule Engine 인스턴스가 스케줄 플로우를 실행합니다. "
                                + "flowId={}, scheduledAt={}",
                        flowId, scheduledAt);
                return;
            }
            reportRedisRecovery();
            flowRunner.runScheduled(definition, scheduledAt);
        } catch (RuntimeException exception) {
            reportRedisFailure("스케줄 실행 선점", exception);
        }
    }

    private Optional<NodeDefinition> scheduleTrigger(FlowDefinition definition) {
        return definition.nodes().stream()
                .filter(node -> node.nodeType() == NodeType.SCHEDULE)
                .findFirst();
    }

    private void registerActiveSafely(Long groupId, Long flowId, boolean publishActiveState) {
        try {
            registerActive(groupId, flowId, publishActiveState);
        } catch (RuntimeException exception) {
            log.error("스케줄 플로우 등록에 실패했습니다. groupId={}, flowId={}",
                    groupId, flowId, exception);
        }
    }

    private Long beginReconciliationSafely() {
        try {
            return executionRedisRepository.beginReconciliation();
        } catch (RuntimeException exception) {
            reportRedisFailure("스케줄 상태 재조정 시작", exception);
            return null;
        }
    }

    private void markActiveSafely(Long flowId) {
        try {
            executionRedisRepository.markActive(flowId);
            reportRedisRecovery();
        } catch (RuntimeException exception) {
            reportRedisFailure("스케줄 활성 상태 반영", exception);
        }
    }

    private void markInactiveSafely(Long flowId, boolean registered) {
        try {
            if (registered) {
                executionRedisRepository.markInactive(flowId);
            } else {
                executionRedisRepository.markInactiveIfPresent(flowId);
            }
            reportRedisRecovery();
        } catch (RuntimeException exception) {
            reportRedisFailure("스케줄 비활성 상태 반영", exception);
        }
    }

    private void reportRedisFailure(String operation, RuntimeException exception) {
        if (redisUnavailable.compareAndSet(false, true)) {
            log.error("스케줄 실행용 Redis에 접근할 수 없습니다. operation={}", operation, exception);
            return;
        }
        log.debug("스케줄 실행용 Redis 장애가 지속 중입니다. operation={}, exceptionType={}",
                operation, exception.getClass().getSimpleName());
    }

    private void reportRedisRecovery() {
        if (redisUnavailable.compareAndSet(true, false)) {
            log.info("스케줄 실행용 Redis 접근이 복구됐습니다.");
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
            int repairedStateCount,
            int totalCount
    ) {
    }
}
