package com.nhnacademy.insightonruleengine.runner.execution.executor;

import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.TimerParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.TimerStateRedisRepository;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TimerNodeExecutor implements NodeExecutor {

    private final NodeParamsParser nodeParamsParser;
    private final TimerStateRedisRepository timerStateRedisRepository;

    @Override
    public NodeType supports() {
        return NodeType.TIMER;
    }

    @Override
    public NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context) {
        TimerParams params = nodeParamsParser.parse(NodeType.TIMER, node.configuration());
        boolean acquired = timerStateRedisRepository.acquire(
                node.nodeId(),
                context.flow().locationId(),
                params.intervalSeconds()
        );
        return NodeExecutionResult.next(acquired ? "true" : "false");
    }
}
