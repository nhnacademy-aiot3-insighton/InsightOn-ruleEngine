package com.nhnacademy.insightonruleengine.runner.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.recovery.FlowRuntimeRecoveryService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BypassFlowRouterTest {

    @Mock
    private FlowRuntimeRecoveryService flowRuntimeRecoveryService;

    private BypassFlowRouter router;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            NodeParamsParser parser = new NodeParamsParser(
                    new ObjectMapper(),
                    validatorFactory.getValidator());
            router = new BypassFlowRouter(flowRuntimeRecoveryService, parser);
        }
    }

    @Test
    void sensorTriggerMatchesOnlyConfiguredSensor() {
        FlowDefinition definition = definition(1L, NodeType.SENSOR, 200L);
        when(flowRuntimeRecoveryService.findActiveFlows(1L, 10L)).thenReturn(List.of(definition));

        List<FlowDefinition> routed = router.route(event(200L));

        assertEquals(List.of(definition), routed);
    }

    @Test
    void locationTriggerMatchesAnySensorInLocation() {
        FlowDefinition definition = definition(2L, NodeType.LOCATION, null);
        when(flowRuntimeRecoveryService.findActiveFlows(1L, 10L)).thenReturn(List.of(definition));

        assertEquals(List.of(definition), router.route(event(999L)));
    }

    @Test
    void sensorTriggerWithDifferentSensorIsExcluded() {
        FlowDefinition definition = definition(3L, NodeType.SENSOR, 200L);
        when(flowRuntimeRecoveryService.findActiveFlows(1L, 10L)).thenReturn(List.of(definition));

        assertEquals(List.of(), router.route(event(201L)));
    }

    @Test
    void scheduleTriggerIsExcludedFromTelemetryExecution() {
        FlowDefinition definition = definition(4L, NodeType.SCHEDULE, null);
        when(flowRuntimeRecoveryService.findActiveFlows(1L, 10L)).thenReturn(List.of(definition));

        assertEquals(List.of(), router.route(event(200L)));
    }

    private FlowDefinition definition(Long flowId, NodeType nodeType, Long sensorId) {
        var configuration = new ObjectMapper().createObjectNode();
        if (sensorId != null) {
            configuration.put("sensorId", sensorId);
        }
        return new FlowDefinition(
                flowId,
                1L,
                10L,
                "Flow " + flowId,
                null,
                FlowStatus.ACTIVE,
                OffsetDateTime.now(),
                List.of(new NodeDefinition(1L, nodeType, configuration)),
                List.of()
        );
    }

    private SensorEvent event(Long sensorId) {
        return new SensorEvent(1L, 10L, sensorId, Map.of("temperature", 20), Instant.now());
    }
}
