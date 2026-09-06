package com.nhnacademy.insightonruleengine.runner.execution.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.client.core.ActuatorCommandRequest;
import com.nhnacademy.insightonruleengine.client.core.CoreActuatorClient;
import com.nhnacademy.insightonruleengine.client.core.LocationResponse;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import jakarta.validation.Validation;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActuatorControlNodeExecutorTest {

    private RecordingCoreActuatorClient coreActuatorClient;
    private ActuatorControlNodeExecutor executor;

    @BeforeEach
    void setUp() {
        coreActuatorClient = new RecordingCoreActuatorClient();
        executor = new ActuatorControlNodeExecutor(
                new NodeParamsParser(
                        new ObjectMapper(),
                        Validation.buildDefaultValidatorFactory().getValidator()
                ),
                coreActuatorClient
        );
    }

    @Test
    void sendsRuleEngineCommandToFlowLocation() {
        FlowDefinition flow = flow();
        NodeDefinition node = flow.nodes().getFirst();

        NodeExecutionResult result = executor.execute(
                node,
                FlowExecutionContext.scheduled(flow, Instant.parse("2026-08-24T09:00:00Z"))
        );

        assertEquals(NodeType.ACTUATOR_CONTROL, executor.supports());
        assertEquals(NodeExecutionResult.complete(), result);
        assertEquals(1L, coreActuatorClient.groupId);
        assertEquals(10L, coreActuatorClient.locationId);
        assertEquals(
                new ActuatorCommandRequest("VENTILATION_FAN", "power", "ON", "RULE_ENGINE"),
                coreActuatorClient.request
        );
    }

    private FlowDefinition flow() {
        return new FlowDefinition(
                1L,
                1L,
                10L,
                "정기 환기",
                null,
                FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-24T00:00:00Z"),
                List.of(new NodeDefinition(
                        2L,
                        NodeType.ACTUATOR_CONTROL,
                        JsonNodeFactory.instance.objectNode()
                                .put("actuatorType", "VENTILATION_FAN")
                                .put("command", "power")
                                .put("commandValue", "ON")
                )),
                List.of()
        );
    }

    private static class RecordingCoreActuatorClient implements CoreActuatorClient {

        private Long groupId;
        private Long locationId;
        private ActuatorCommandRequest request;

        @Override
        public void updateActuatorState(Long groupId, Long locationId, ActuatorCommandRequest request) {
            this.groupId = groupId;
            this.locationId = locationId;
            this.request = request;
        }

        @Override
        public LocationResponse getLocation(Long locationId) {
            throw new UnsupportedOperationException("이 테스트에서는 사용하지 않습니다.");
        }
    }
}
