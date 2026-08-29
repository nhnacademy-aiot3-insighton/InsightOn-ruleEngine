package com.nhnacademy.insightonruleengine.runner.observability;

import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExecutionLoggerImpl implements ExecutionLogger {

    @Override
    public void eventRouted(SensorEvent event, int flowCount) {
        log.info(
                "Rule event routed. flowCount={}, sensorEvent={}",
                flowCount,
                sensorEventFields(event)
        );
    }

    @Override
    public void flowStarted(ExecutionLogContext context, Long triggerNodeId) {
        log.info(
                "Flow execution started. executionId={}, triggerType={}, flowId={}, triggerNodeId={}, trigger={}",
                context.executionId(),
                context.triggerType(),
                context.flowId(),
                triggerNodeId,
                triggerFields(context)
        );
    }

    @Override
    public void flowFinished(ExecutionLogContext context, Long terminalNodeId, boolean terminalActionReached) {
        log.info(
                "Flow execution finished. executionId={}, flowId={}, terminalNodeId={}, "
                        + "terminalActionReached={}, triggerType={}, trigger={}",
                context.executionId(),
                context.flowId(),
                terminalNodeId,
                terminalActionReached,
                context.triggerType(),
                triggerFields(context)
        );
    }


    @Override
    public void nodeStarted(ExecutionLogContext context, NodeDefinition node) {
        log.info(
                "Node execution started. executionId={}, flowId={}, nodeId={}, nodeType={}, "
                        + "sensorId={}, locationId={}",
                context.executionId(),
                context.flowId(),
                node.nodeId(),
                node.nodeType(),
                context.sensorId(),
                context.locationId()
        );
    }

    @Override
    public void nodeFinished(ExecutionLogContext context, NodeDefinition node, NodeExecutionResult result) {
        log.info(
                "Node execution finished. executionId={}, flowId={}, nodeId={}, nodeType={}, "
                        + "outputPort={}, terminal={}",
                context.executionId(),
                context.flowId(),
                node.nodeId(),
                node.nodeType(),
                result.outputPort(),
                result.terminal()
        );
    }

    @Override
    public void nodeFailed(ExecutionLogContext context, NodeDefinition node, RuntimeException exception) {
        log.warn(
                "Node execution failed. executionId={}, flowId={}, nodeId={}, nodeType={}, "
                        + "triggerType={}, trigger={}",
                context.executionId(),
                context.flowId(),
                node.nodeId(),
                node.nodeType(),
                context.triggerType(),
                triggerFields(context),
                exception
        );
    }

    @Override
    public void flowFailed(ExecutionLogContext context, RuntimeException exception) {
        log.warn(
                "Flow execution failed. executionId={}, flowId={}, exceptionType={}, message={}, "
                        + "triggerType={}, trigger={}",
                context.executionId(),
                context.flowId(),
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                context.triggerType(),
                triggerFields(context),
                exception
        );
    }

    private Map<String, Object> sensorEventFields(SensorEvent event) {
        return sensorEventFields(
                event.groupId(),
                event.locationId(),
                event.sensorId(),
                event.metrics(),
                event.timestamp()
        );
    }

    private Map<String, Object> triggerFields(ExecutionLogContext context) {
        return sensorEventFields(
                context.groupId(),
                context.locationId(),
                context.sensorId(),
                context.metrics(),
                context.timestamp()
        );
    }

    private Map<String, Object> sensorEventFields(
            Long groupId,
            Long locationId,
            Long sensorId,
            Map<String, Object> metrics,
            Instant timestamp
    ) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("groupId", groupId);
        fields.put("locationId", locationId);
        fields.put("sensorId", sensorId);
        fields.put("metrics", metrics);
        fields.put("timestamp", timestamp);
        return fields;
    }
}
