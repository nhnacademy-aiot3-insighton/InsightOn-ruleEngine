package com.nhnacademy.insightonruleengine.runner.execution.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.EventGateParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.EventGateStateRedisRepository;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventGateNodeExecutorTest {

    private final NodeParamsParser nodeParamsParser = mock(NodeParamsParser.class);
    private final EventGateStateRedisRepository repository = mock(EventGateStateRedisRepository.class);
    private final EventGateNodeExecutor executor = new EventGateNodeExecutor(nodeParamsParser, repository);
    private final NodeDefinition node = new NodeDefinition(
            100L,
            NodeType.EVENT_GATE,
            JsonNodeFactory.instance.objectNode()
    );
    private final FlowExecutionContext context = mock(FlowExecutionContext.class);

    @BeforeEach
    void setUp() {
        FlowDefinition flow = mock(FlowDefinition.class);
        when(flow.flowId()).thenReturn(10L);
        when(context.flow()).thenReturn(flow);
        when(nodeParamsParser.<EventGateParams>parse(eq(NodeType.EVENT_GATE), any()))
                .thenReturn(new EventGateParams(3, 300, 600));
    }

    @Test
    void passedGateUsesTruePort() {
        when(repository.tryPass(10L, 100L, 3, 300, 600)).thenReturn(true);

        assertEquals("true", executor.execute(node, context).outputPort());
        verify(repository).tryPass(10L, 100L, 3, 300, 600);
    }

    @Test
    void blockedGateUsesFalsePort() {
        when(repository.tryPass(10L, 100L, 3, 300, 600)).thenReturn(false);

        assertEquals("false", executor.execute(node, context).outputPort());
    }
}
