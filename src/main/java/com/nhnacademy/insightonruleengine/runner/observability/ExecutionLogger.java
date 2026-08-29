package com.nhnacademy.insightonruleengine.runner.observability;

import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.runner.model.ExecutionTriggerType;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExecutionLogger {

    private static final int DEFAULT_MAX_TRACKED_FAILURE_SCOPES = 10_000;

    private final int maxTrackedFailureScopes;
    private final ConcurrentMap<RoutingFailureScope, FailureCounter> routingFailures =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<FlowFailureScope, FlowFailureState> flowFailures =
            new ConcurrentHashMap<>();

    public ExecutionLogger() {
        this(DEFAULT_MAX_TRACKED_FAILURE_SCOPES);
    }

    ExecutionLogger(int maxTrackedFailureScopes) {
        if (maxTrackedFailureScopes <= 0) {
            throw new IllegalArgumentException("maxTrackedFailureScopes는 양수여야 합니다.");
        }
        this.maxTrackedFailureScopes = maxTrackedFailureScopes;
    }

    public void eventRouted(SensorEvent event, int flowCount) {
        FailureCounter recovered = routingFailures.remove(RoutingFailureScope.from(event));
        if (recovered != null) {
            log.info(
                    "센서 이벤트 라우팅이 복구됐습니다. groupId={}, locationId={}, sensorId={}, "
                            + "timestamp={}, suppressedFailureCount={}",
                    event.groupId(),
                    event.locationId(),
                    event.sensorId(),
                    event.timestamp(),
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
        AtomicBoolean shouldLog = new AtomicBoolean();
        RoutingFailureScope scope = RoutingFailureScope.from(event);
        synchronized (routingFailures) {
            makeRoomForNewScope(routingFailures, scope);
            routingFailures.compute(scope, (ignored, current) -> {
                if (current == null) {
                    shouldLog.set(true);
                    return new FailureCounter(0L);
                }
                return current.incremented();
            });
        }
        if (!shouldLog.get()) {
            return;
        }
        log.error(
                "센서 이벤트 라우팅에 실패했습니다. groupId={}, locationId={}, sensorId={}, timestamp={}, "
                        + "exceptionType={}, message={}",
                event.groupId(),
                event.locationId(),
                event.sensorId(),
                event.timestamp(),
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                exception
        );
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
                            + "suppressedFailureCount={}",
                    context.triggerType(),
                    context.flowId(),
                    context.groupId(),
                    context.locationId(),
                    context.sensorId(),
                    context.timestamp(),
                    recovered.signature().nodeId(),
                    recovered.signature().exceptionType(),
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
        FlowFailureScope scope = FlowFailureScope.from(context);
        FlowFailureSignature signature = new FlowFailureSignature(
                node == null ? null : node.nodeId(),
                node == null ? null : node.nodeType(),
                exception.getClass().getSimpleName()
        );
        AtomicBoolean shouldLog = new AtomicBoolean();
        synchronized (flowFailures) {
            makeRoomForNewScope(flowFailures, scope);
            flowFailures.compute(scope, (ignored, current) -> {
                if (current == null || !current.signature().equals(signature)) {
                    shouldLog.set(true);
                    return new FlowFailureState(signature, 0L);
                }
                return current.incremented();
            });
        }
        if (!shouldLog.get()) {
            return;
        }
        log.error(
                "플로우 실행 실패. executionId={}, triggerType={}, flowId={}, groupId={}, locationId={}, "
                        + "sensorId={}, triggeredAt={}, nodeId={}, nodeType={}, exceptionType={}, message={}",
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
                exception.getMessage(),
                exception
        );
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

    private record FailureCounter(long suppressedCount) {

        private FailureCounter incremented() {
            return new FailureCounter(suppressedCount + 1L);
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

    private record FlowFailureSignature(Long nodeId, NodeType nodeType, String exceptionType) {
    }

    private record FlowFailureState(FlowFailureSignature signature, long suppressedCount) {

        private FlowFailureState incremented() {
            return new FlowFailureState(signature, suppressedCount + 1L);
        }
    }
}
