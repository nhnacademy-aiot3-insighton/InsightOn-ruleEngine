package com.nhnacademy.insightonruleengine.runner.execution.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.TimerParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.TimerStateRedisRepository;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimerNodeExecutorTest {

    private final NodeParamsParser nodeParamsParser = mock(NodeParamsParser.class);
    private final TimerStateRedisRepository timerStateRedisRepository =
            mock(TimerStateRedisRepository.class);
    private final TimerNodeExecutor executor = new TimerNodeExecutor(
            nodeParamsParser,
            timerStateRedisRepository
    );
    private final NodeDefinition node = new NodeDefinition(
            100L,
            NodeType.TIMER,
            JsonNodeFactory.instance.objectNode()
    );
    private final FlowExecutionContext context = mock(FlowExecutionContext.class);

    @BeforeEach
    void setUp() {
        FlowDefinition flow = mock(FlowDefinition.class);
        when(flow.locationId()).thenReturn(20L);
        when(context.flow()).thenReturn(flow);
        when(nodeParamsParser.<TimerParams>parse(eq(NodeType.TIMER), any()))
                .thenReturn(new TimerParams(60));
    }

    @Test
    void acquiredIntervalUsesTruePort() {
        when(timerStateRedisRepository.acquire(100L, 20L, 60)).thenReturn(true);

        assertEquals("true", executor.execute(node, context).outputPort());
    }

    @Test
    void occupiedIntervalUsesFalsePort() {
        when(timerStateRedisRepository.acquire(100L, 20L, 60)).thenReturn(false);

        assertEquals("false", executor.execute(node, context).outputPort());
    }
}
