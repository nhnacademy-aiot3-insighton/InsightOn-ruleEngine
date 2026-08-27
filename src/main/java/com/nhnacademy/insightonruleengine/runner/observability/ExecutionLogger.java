package com.nhnacademy.insightonruleengine.runner.observability;

import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExecutionLogger {

    public void eventRouted(SensorEvent event, int flowCount) {
        log.debug(
                "센서 이벤트 라우팅 완료. groupId={}, locationId={}, sensorId={}, flowCount={}, timestamp={}",
                event.groupId(),
                event.locationId(),
                event.sensorId(),
                flowCount,
                event.timestamp()
        );
    }

    public void flowFinished(
            ExecutionLogContext context,
            Long terminalNodeId,
            boolean terminalActionReached
    ) {
        log.debug(
                "플로우 실행 완료. executionId={}, triggerType={}, flowId={}, terminalNodeId={}, actionReached={}",
                context.executionId(),
                context.triggerType(),
                context.flowId(),
                terminalNodeId,
                terminalActionReached
        );
    }

    public void flowFailed(
            ExecutionLogContext context,
            NodeDefinition node,
            RuntimeException exception
    ) {
        log.error(
                "플로우 실행 실패. executionId={}, triggerType={}, flowId={}, nodeId={}, nodeType={}, "
                        + "exceptionType={}, message={}",
                context.executionId(),
                context.triggerType(),
                context.flowId(),
                node == null ? null : node.nodeId(),
                node == null ? null : node.nodeType(),
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                exception
        );
    }
}
