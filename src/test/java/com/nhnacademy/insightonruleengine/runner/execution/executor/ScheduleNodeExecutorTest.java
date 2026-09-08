package com.nhnacademy.insightonruleengine.runner.execution.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleNodeExecutorTest {

    private final ScheduleNodeExecutor executor = new ScheduleNodeExecutor();

    @Test
    void continuesThroughOutPort() {
        FlowDefinition flow = flow();
        NodeDefinition node = flow.nodes().getFirst();

        NodeExecutionResult result = executor.execute(
                node,
                FlowExecutionContext.scheduled(flow, Instant.parse("2026-08-24T09:00:00Z"))
        );

        assertEquals(NodeType.SCHEDULE, executor.supports());
        assertEquals(NodeExecutionResult.next("out"), result);
    }

    private FlowDefinition flow() {
        return new FlowDefinition(
                1L,
                1L,
                10L,
                "정기 실행",
                null,
                FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-24T00:00:00Z"),
                List.of(new NodeDefinition(
                        1L,
                        NodeType.SCHEDULE,
                        JsonNodeFactory.instance.objectNode().put("cron", "0 0 * * * *")
                )),
                List.of()
        );
    }
}
