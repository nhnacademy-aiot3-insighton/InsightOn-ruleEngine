package com.nhnacademy.insightonruleengine.runner.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.runner.model.ExecutionTriggerType;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import feign.FeignException;
import feign.RetryableException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.expression.ExpressionException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExecutionLogger {

    private static final int DEFAULT_MAX_TRACKED_FAILURE_SCOPES = 10_000;

    private final int maxTrackedFailureScopes;
    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<RoutingFailureScope, FailureCounter> routingFailures =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<FlowFailureScope, FlowFailureState> flowFailures =
            new ConcurrentHashMap<>();

    public ExecutionLogger() {
        this(DEFAULT_MAX_TRACKED_FAILURE_SCOPES, new SimpleMeterRegistry());
    }

    @Autowired
    public ExecutionLogger(MeterRegistry meterRegistry) {
        this(DEFAULT_MAX_TRACKED_FAILURE_SCOPES, meterRegistry);
    }

    ExecutionLogger(int maxTrackedFailureScopes) {
        this(maxTrackedFailureScopes, new SimpleMeterRegistry());
    }

    ExecutionLogger(int maxTrackedFailureScopes, MeterRegistry meterRegistry) {
        if (maxTrackedFailureScopes <= 0) {
            throw new IllegalArgumentException("maxTrackedFailureScopes는 양수여야 합니다.");
        }
        this.maxTrackedFailureScopes = maxTrackedFailureScopes;
        this.meterRegistry = meterRegistry;
    }

    public void eventRouted(SensorEvent event, int flowCount) {
        FailureCounter recovered = routingFailures.remove(RoutingFailureScope.from(event));
        if (recovered != null) {
            log.info(
                    "센서 이벤트 라우팅이 복구됐습니다. groupId={}, locationId={}, sensorId={}, "
                            + "timestamp={}, lastFailureKind={}, lastExceptionType={}, "
                            + "lastMessage={}, suppressedFailureCount={}",
                    event.groupId(),
                    event.locationId(),
                    event.sensorId(),
                    event.timestamp(),
                    recovered.lastFailureKind(),
                    recovered.lastExceptionType(),
                    recovered.lastMessage(),
                    recovered.suppressedCount()
            );
        }
        log.debug(
                "센서 이벤트 라우팅 완료. groupId={}, locationId={}, sensorId={}, flowCount={}, timestamp={}",
                event.groupId(),
                event.locationId(),
                event.sensorId(),
                flowCount,
                event.timestamp()
        );
    }

    public void routingFailed(SensorEvent event, RuntimeException exception) {
        FailureKind failureKind = classify(exception);
        AtomicBoolean shouldLog = new AtomicBoolean();
        RoutingFailureScope scope = RoutingFailureScope.from(event);
        synchronized (routingFailures) {
            makeRoomForNewScope(routingFailures, scope);
            routingFailures.compute(scope, (ignored, current) -> {
                if (current == null || !current.matches(failureKind, exception)) {
                    shouldLog.set(true);
                    return FailureCounter.first(failureKind, exception);
                }
                return current.incremented(failureKind, exception);
            });
        }
        recordFailure("routing", failureKind);
        if (!shouldLog.get()) {
            return;
        }
        logFailure(
                failureKind,
                "센서 이벤트 라우팅에 실패했습니다. packetDropped=true, retry=false, "
                        + "failureKind={}, groupId={}, locationId={}, sensorId={}, timestamp={}, "
                        + "exceptionType={}, message={}",
                failureKind,
                event.groupId(),
                event.locationId(),
                event.sensorId(),
                event.timestamp(),
                exception.getClass().getSimpleName(),
                exception.getMessage()
        );
        log.debug("센서 이벤트 라우팅 실패 상세.", exception);
    }

    public void flowFinished(
            ExecutionLogContext context,
            Long terminalNodeId,
            boolean terminalActionReached
    ) {
        FlowFailureState recovered = flowFailures.remove(FlowFailureScope.from(context));
        if (recovered != null) {
            log.info(
                    "플로우 실행이 복구됐습니다. triggerType={}, flowId={}, groupId={}, locationId={}, "
                            + "sensorId={}, triggeredAt={}, lastNodeId={}, lastExceptionType={}, "
                            + "lastFailureKind={}, lastMessage={}, suppressedFailureCount={}",
                    context.triggerType(),
                    context.flowId(),
                    context.groupId(),
                    context.locationId(),
                    context.sensorId(),
                    context.timestamp(),
                    recovered.signature().nodeId(),
                    recovered.signature().exceptionType(),
                    recovered.signature().failureKind(),
                    recovered.lastMessage(),
                    recovered.suppressedCount()
            );
        }
        log.debug(
                "플로우 실행 완료. executionId={}, triggerType={}, flowId={}, groupId={}, locationId={}, "
                        + "sensorId={}, triggeredAt={}, terminalNodeId={}, actionReached={}",
                context.executionId(),
                context.triggerType(),
                context.flowId(),
                context.groupId(),
                context.locationId(),
                context.sensorId(),
                context.timestamp(),
                terminalNodeId,
                terminalActionReached
        );
    }

    public void flowFailed(
            ExecutionLogContext context,
            NodeDefinition node,
            RuntimeException exception
    ) {
        FailureKind failureKind = classify(exception);
        FlowFailureScope scope = FlowFailureScope.from(context);
        FlowFailureSignature signature = new FlowFailureSignature(
                node == null ? null : node.nodeId(),
                node == null ? null : node.nodeType(),
                exception.getClass().getSimpleName(),
                failureKind
        );
        AtomicBoolean shouldLog = new AtomicBoolean();
        synchronized (flowFailures) {
            makeRoomForNewScope(flowFailures, scope);
            flowFailures.compute(scope, (ignored, current) -> {
                if (current == null || !current.signature().equals(signature)) {
                    shouldLog.set(true);
                    return new FlowFailureState(signature, exception.getMessage(), 0L);
                }
                return current.incremented(exception);
            });
        }
        recordFailure("flow", failureKind);
        if (!shouldLog.get()) {
            return;
        }
        logFailure(
                failureKind,
                "플로우 실행 실패. packetDropped=true, retry=false, failureKind={}, executionId={}, "
                        + "triggerType={}, flowId={}, groupId={}, locationId={}, "
                        + "sensorId={}, triggeredAt={}, nodeId={}, nodeType={}, exceptionType={}, message={}",
                failureKind,
                context.executionId(),
                context.triggerType(),
                context.flowId(),
                context.groupId(),
                context.locationId(),
                context.sensorId(),
                context.timestamp(),
                signature.nodeId(),
                signature.nodeType(),
                signature.exceptionType(),
                exception.getMessage()
        );
        log.debug("플로우 실행 실패 상세.", exception);
    }

    int trackedRoutingFailureCount() {
        return routingFailures.size();
    }

    int trackedFlowFailureCount() {
        return flowFailures.size();
    }

    private <K, V> void makeRoomForNewScope(ConcurrentMap<K, V> failures, K scope) {
        if (failures.containsKey(scope)) {
            return;
        }
        while (failures.size() >= maxTrackedFailureScopes) {
            K evictionCandidate = failures.keySet().stream().findFirst().orElse(null);
            if (evictionCandidate == null) {
                return;
            }
            failures.remove(evictionCandidate);
        }
    }

    private void recordFailure(String stage, FailureKind failureKind) {
        meterRegistry.counter(
                "rule_engine.execution.failures",
                "stage", stage,
                "kind", failureKind.name().toLowerCase()
        ).increment();
    }

    private void logFailure(FailureKind failureKind, String message, Object... arguments) {
        if (failureKind == FailureKind.TRANSIENT_DEPENDENCY) {
            log.warn(message, arguments);
            return;
        }
        log.error(message, arguments);
    }

    private FailureKind classify(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            FailureKind kind = classifySingle(current);
            if (kind != null) {
                return kind;
            }
            current = current.getCause();
        }
        return FailureKind.INTERNAL;
    }

    // 원인 체인의 예외 하나를 판정합니다. 어떤 그룹에도 안 걸리면 null을 반환해 다음 cause를 보게 합니다.
    // 판정 순서(TRANSIENT_DEPENDENCY -> PERMANENT_REJECTED -> INTERNAL -> Feign -> PERMANENT_CONFIGURATION
    // -> TRANSIENT_DEPENDENCY)는 기존과 동일하게 유지합니다.
    private FailureKind classifySingle(Throwable current) {
        if (isTransientDependency(current)) {
            return FailureKind.TRANSIENT_DEPENDENCY;
        }
        if (current instanceof NonTransientDataAccessException) {
            return FailureKind.PERMANENT_REJECTED;
        }
        if (current instanceof DataAccessException) {
            return FailureKind.INTERNAL;
        }
        if (current instanceof FeignException feignException) {
            return classifyFeignException(feignException);
        }
        if (isPermanentConfiguration(current)) {
            return FailureKind.PERMANENT_CONFIGURATION;
        }
        if (current instanceof IOException) {
            return FailureKind.TRANSIENT_DEPENDENCY;
        }
        return null;
    }

    private boolean isTransientDependency(Throwable current) {
        return current instanceof RetryableException
                || current instanceof AmqpException
                || current instanceof RedisConnectionFailureException
                || current instanceof TransientDataAccessException
                || current instanceof RecoverableDataAccessException
                || current instanceof SocketTimeoutException
                || current instanceof TimeoutException;
    }

    private FailureKind classifyFeignException(FeignException feignException) {
        int status = feignException.status();
        if (status >= 500 || status == 408 || status == 429 || status < 0) {
            return FailureKind.TRANSIENT_DEPENDENCY;
        }
        return FailureKind.PERMANENT_REJECTED;
    }

    private boolean isPermanentConfiguration(Throwable current) {
        return current instanceof JsonProcessingException
                || current instanceof ConstraintViolationException
                || current instanceof ExpressionException
                || current instanceof IllegalArgumentException;
    }

    private enum FailureKind {
        TRANSIENT_DEPENDENCY,
        PERMANENT_CONFIGURATION,
        PERMANENT_REJECTED,
        INTERNAL
    }

    private record FailureCounter(
            FailureKind lastFailureKind,
            String lastExceptionType,
            String lastMessage,
            long suppressedCount
    ) {

        private static FailureCounter first(FailureKind failureKind, RuntimeException exception) {
            return new FailureCounter(
                    failureKind,
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    0L
            );
        }

        private FailureCounter incremented(FailureKind failureKind, RuntimeException exception) {
            return new FailureCounter(
                    failureKind,
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    suppressedCount + 1L
            );
        }

        private boolean matches(FailureKind failureKind, RuntimeException exception) {
            return lastFailureKind == failureKind
                    && lastExceptionType.equals(exception.getClass().getSimpleName());
        }
    }

    private record RoutingFailureScope(Long groupId, Long locationId) {

        private static RoutingFailureScope from(SensorEvent event) {
            return new RoutingFailureScope(event.groupId(), event.locationId());
        }
    }

    private record FlowFailureScope(ExecutionTriggerType triggerType, Long flowId) {

        private static FlowFailureScope from(ExecutionLogContext context) {
            return new FlowFailureScope(context.triggerType(), context.flowId());
        }
    }

    private record FlowFailureSignature(
            Long nodeId,
            NodeType nodeType,
            String exceptionType,
            FailureKind failureKind
    ) {
    }

    private record FlowFailureState(
            FlowFailureSignature signature,
            String lastMessage,
            long suppressedCount
    ) {

        private FlowFailureState incremented(RuntimeException exception) {
            return new FlowFailureState(signature, exception.getMessage(), suppressedCount + 1L);
        }
    }
}
