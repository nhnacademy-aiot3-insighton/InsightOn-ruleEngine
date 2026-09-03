package com.nhnacademy.insightonruleengine.runner.execution.executor;

import com.nhnacademy.insightonruleengine.config.ScheduleExecutionProperties;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.TimeWindowParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TimeWindowNodeExecutor implements NodeExecutor {

    private final NodeParamsParser nodeParamsParser;
    private final ScheduleExecutionProperties timeProperties;

    @Override
    public NodeType nodeType() {
        return NodeType.TIME_WINDOW;
    }

    @Override
    public NodeExecutionResult execute(NodeDefinition node, FlowExecutionContext context) {
        TimeWindowParams params = nodeParamsParser.parse(
                NodeType.TIME_WINDOW,
                node.configuration()
        );
        LocalTime executionTime = context.timestamp()
                .atZone(timeProperties.zoneId())
                .toLocalTime();

        return NodeExecutionResult.next(isWithinWindow(executionTime, params) ? "true" : "false");
    }

    private boolean isWithinWindow(LocalTime executionTime, TimeWindowParams params) {
        LocalTime startTime = params.startTime();
        LocalTime endTime = params.endTime();
        if (startTime.isBefore(endTime)) {
            return !executionTime.isBefore(startTime) && executionTime.isBefore(endTime);
        }
        return !executionTime.isBefore(startTime) || executionTime.isBefore(endTime);
    }
}
