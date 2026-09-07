package com.nhnacademy.insightonruleengine.runner.execution.executor;

import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.EventGateParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.EventGateStateRedisRepository;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 반복 도달과 재알림 간격으로 Action 실행을 통제한다.
 * Action 종류와 무관한 Filter라서 ALERT과 ACTUATOR_CONTROL이 같은 억제 정책을 공유할 수 있다.
 */
@Component
@RequiredArgsConstructor
public class EventGateNodeExecutor implements NodeExecutor {

    private final NodeParamsParser nodeParamsParser;
    private final EventGateStateRedisRepository eventGateStateRedisRepository;

    @Override
    public NodeType supports() {
        return NodeType.EVENT_GATE;
    }

    @Override
    public NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context) {
        EventGateParams params = nodeParamsParser.parse(NodeType.EVENT_GATE, node.configuration());
        boolean passed = eventGateStateRedisRepository.tryPass(
                context.flow().flowId(),
                node.nodeId(),
                params.requiredCount(),
                params.effectiveCountWindowSeconds(),
                params.cooldownSeconds()
        );
        return NodeExecutionResult.next(passed ? "true" : "false");
    }
}
