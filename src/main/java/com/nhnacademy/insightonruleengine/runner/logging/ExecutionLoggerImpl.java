package com.nhnacademy.insightonruleengine.runner.logging;

import com.nhnacademy.insightonruleengine.flow.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.runner.dto.NodeExecutionResult;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExecutionLoggerImpl implements ExecutionLogger {

    @Override
    public void eventRouted(SensorEvent event, int flowCount) {
        log.info(
                "Rule event routed. flowCount={}, groupId={}, locationId={}, sensorId={}, timestamp={}",
                flowCount,
                event.groupId(),
                event.locationId(),
                event.sensorId(),
                event.timestamp()
        );
    }

    @Override
    public void flowStarted(ExecutionLogContext context, Long triggerNodeId) {
        log.info(
                "Flow execution started. executionId={}, flowId={}, groupId={}, locationId={}, "
                        + "sensorId={}, timestamp={}, triggerNodeId={}",
                context.executionId(),
                context.flowId(),
                context.groupId(),
                context.locationId(),
                context.sensorId(),
                context.timestamp(),
                triggerNodeId
        );
    }

    @Override
    public void flowFinished(ExecutionLogContext context, Long terminalNodeId, boolean terminalActionReached) {
        log.info(
                "Flow execution finished. executionId={}, flowId={}, groupId={}, locationId={}, "
                        + "sensorId={}, timestamp={}, terminalNodeId={}, terminalActionReached={}",
                context.executionId(),
                context.flowId(),
                context.groupId(),
                context.locationId(),
                context.sensorId(),
                context.timestamp(),
                terminalNodeId,
                terminalActionReached
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
                        + "sensorId={}, locationId={}",
                context.executionId(),
                context.flowId(),
                node.nodeId(),
                node.nodeType(),
                context.sensorId(),
                context.locationId(),
                exception
        );
    }

    @Override
    public void flowFailed(ExecutionLogContext context, RuntimeException exception) {
        log.warn(
                "Flow execution failed. executionId={}, flowId={}, groupId={}, locationId={}, "
                        + "sensorId={}, exceptionType={}, message={}",
                context.executionId(),
                context.flowId(),
                context.groupId(),
                context.locationId(),
                context.sensorId(),
                exception.getClass().getSimpleName(),
                exception.getMessage()
        );
    }
}
