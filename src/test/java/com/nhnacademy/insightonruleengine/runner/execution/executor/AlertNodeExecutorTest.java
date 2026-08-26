package com.nhnacademy.insightonruleengine.runner.execution.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.AlertParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.Severity;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.application.action.ActionPublisher;
import com.nhnacademy.insightonruleengine.runner.execution.state.alert.AlertCountService;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.model.action.EngineAlertActionEvent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertNodeExecutorTest {

    @Mock
    private NodeParamsParser nodeParamsParser;

    @Mock
    private AlertCountService alertCountService;

    @Mock
    private ActionPublisher actionPublisher;

    private AlertNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new AlertNodeExecutor(nodeParamsParser, alertCountService, actionPublisher);
    }

    @Test
    void suppressesAlertUntilCountAndCooldownPolicyAllowsIt() {
        NodeDefinition node = node();
        FlowExecutionContext context = context();
        AlertParams params = params();
        when(nodeParamsParser.parse(NodeType.ALERT, node.configuration())).thenReturn(params);
        when(alertCountService.shouldPublish(100L, 200L, params)).thenReturn(false);

        NodeExecutionResult result = executor.execute(node, context);

        assertTrue(result.terminal());
        verify(actionPublisher, never()).publishAlert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publishesAlertWithFlowAndTriggerValues() {
        NodeDefinition node = node();
        FlowExecutionContext context = context();
        AlertParams params = params();
        when(nodeParamsParser.parse(NodeType.ALERT, node.configuration())).thenReturn(params);
        when(alertCountService.shouldPublish(100L, 200L, params)).thenReturn(true);

        NodeExecutionResult result = executor.execute(node, context);

        ArgumentCaptor<EngineAlertActionEvent> captor = ArgumentCaptor.forClass(EngineAlertActionEvent.class);
        verify(actionPublisher).publishAlert(captor.capture());
        EngineAlertActionEvent event = captor.getValue();
        assertTrue(result.terminal());
        assertNotNull(event.eventId());
        assertEquals(4, event.eventId().version());
        assertEquals(1L, event.groupId());
        assertEquals(10L, event.locationId());
        assertEquals(100L, event.flowId());
        assertEquals("고온 경보", event.title());
        assertEquals(Map.of("temperature", 31.5), event.triggerValue());
    }

    private NodeDefinition node() {
        return new NodeDefinition(200L, NodeType.ALERT, JsonNodeFactory.instance.objectNode());
    }

    private AlertParams params() {
        return new AlertParams("고온 경보", Severity.CRITICAL, "온도가 기준을 초과했습니다.", 1, null, 0);
    }

    private FlowExecutionContext context() {
        FlowDefinition flow = new FlowDefinition(
                100L,
                1L,
                10L,
                "경보 Flow",
                null,
                FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-24T00:00:00Z"),
                List.of(node()),
                List.of()
        );
        SensorEvent event = new SensorEvent(
                1L,
                10L,
                300L,
                Map.of("temperature", 31.5),
                Instant.parse("2026-08-24T00:00:00Z")
        );
        return new FlowExecutionContext(flow, event);
    }
}
