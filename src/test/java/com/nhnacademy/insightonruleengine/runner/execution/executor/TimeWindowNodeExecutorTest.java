package com.nhnacademy.insightonruleengine.runner.execution.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.config.ScheduleExecutionProperties;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.TimeWindowParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimeWindowNodeExecutorTest {

    private final NodeParamsParser nodeParamsParser = mock(NodeParamsParser.class);
    private final TimeWindowNodeExecutor executor = new TimeWindowNodeExecutor(
            nodeParamsParser,
            new ScheduleExecutionProperties("Asia/Seoul", Duration.ofMinutes(10), 1)
    );
    private final NodeDefinition node = new NodeDefinition(
            10L,
            NodeType.TIME_WINDOW,
            JsonNodeFactory.instance.objectNode()
    );

    @BeforeEach
    void setUpParams() {
        when(nodeParamsParser.<TimeWindowParams>parse(eq(NodeType.TIME_WINDOW), any()))
                .thenReturn(new TimeWindowParams(LocalTime.of(9, 0), LocalTime.of(18, 0)));
    }

    @Test
    void startTimeIsIncludedAndEndTimeIsExcluded() {
        assertEquals("true", executeAt("2026-08-30T00:00:00Z"));
        assertEquals("false", executeAt("2026-08-30T09:00:00Z"));
    }

    @Test
    void evaluatesUsingConfiguredTimeZone() {
        assertEquals("false", executeAt("2026-08-29T23:59:59Z"));
        assertEquals("true", executeAt("2026-08-30T00:00:00Z"));
    }

    @Test
    void supportsWindowAcrossMidnight() {
        when(nodeParamsParser.<TimeWindowParams>parse(eq(NodeType.TIME_WINDOW), any()))
                .thenReturn(new TimeWindowParams(LocalTime.of(22, 0), LocalTime.of(6, 0)));

        assertEquals("true", executeAt("2026-08-30T14:00:00Z"));
        assertEquals("true", executeAt("2026-08-30T20:59:59Z"));
        assertEquals("false", executeAt("2026-08-30T21:00:00Z"));
        assertEquals("false", executeAt("2026-08-30T03:00:00Z"));
    }

    private String executeAt(String instant) {
        FlowExecutionContext context = mock(FlowExecutionContext.class);
        when(context.timestamp()).thenReturn(Instant.parse(instant));
        return executor.execute(node, context).outputPort();
    }
}
