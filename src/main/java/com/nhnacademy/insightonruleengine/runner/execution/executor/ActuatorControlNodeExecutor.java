package com.nhnacademy.insightonruleengine.runner.execution.executor;

import com.nhnacademy.insightonruleengine.client.core.ActuatorCommandRequest;
import com.nhnacademy.insightonruleengine.client.core.CoreActuatorClient;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.ActuatorControlParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ActuatorControlNodeExecutor implements NodeExecutor {

    private final NodeParamsParser nodeParamsParser;
    private final CoreActuatorClient coreActuatorClient;

    @Override
    public NodeType nodeType() {
        return NodeType.ACTUATOR_CONTROL;
    }

    @Override
    public NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context) {
        ActuatorControlParams params = nodeParamsParser.parse(
                NodeType.ACTUATOR_CONTROL,
                node.configuration()
        );
        coreActuatorClient.updateActuatorState(
                context.flow().locationId(),
                ActuatorCommandRequest.from(params)
        );
        log.debug(
                "액추에이터 명령 전달 완료. flowId={}, locationId={}, actuatorType={}, command={}",
                context.flow().flowId(),
                context.flow().locationId(),
                params.actuatorType(),
                params.command()
        );
        return NodeExecutionResult.complete();
    }
}
