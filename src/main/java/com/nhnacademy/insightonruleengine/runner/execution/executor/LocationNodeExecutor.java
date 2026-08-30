package com.nhnacademy.insightonruleengine.runner.execution.executor;

import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocationNodeExecutor implements NodeExecutor {

    private final NodeParamsParser nodeParamsParser;

    @Override
    public NodeType supports() {
        return NodeType.LOCATION;
    }

    @Override
    public NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context) {
        nodeParamsParser.parse(NodeType.LOCATION, node.configuration());
        validateCurrentPacket(context);
        return NodeExecutionResult.next("out");
    }

    private void validateCurrentPacket(FlowExecutionContext context) {
        if (context == null || context.event() == null) {
            throw new IllegalArgumentException("Location Node에는 센서 이벤트가 필요합니다.");
        }
        if (!Objects.equals(context.flow().locationId(), context.event().locationId())) {
            throw new IllegalArgumentException("Flow locationId와 event locationId가 일치하지 않습니다.");
        }
        context.event().metrics().keySet().forEach(metricKey -> {
            if (metricKey == null || metricKey.isBlank()) {
                throw new IllegalArgumentException("metric key는 필수입니다.");
            }
        });
    }
}
