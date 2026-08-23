package com.nhnacademy.insightonruleengine.flow.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.flow.validation.domain.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.runner.evaluator.ThresholdEvaluator;
import com.nhnacademy.insightonruleengine.runner.executor.NodeExecutor;
import com.nhnacademy.insightonruleengine.runner.executor.NodeExecutorRegistry;
import jakarta.validation.Validation;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlowActivationValidatorTest {

    @Test
    void invalidStoredConfigurationPreventsActivation() {
        FlowStructureValidator structureValidator = mock(FlowStructureValidator.class);
        NodeExecutor executor = mock(NodeExecutor.class);
        when(executor.supports()).thenReturn(NodeType.SENSOR);
        NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(executor));
        NodeParamsParser parser = new NodeParamsParser(
                new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator());
        FlowActivationValidator validator = new FlowActivationValidator(
                structureValidator,
                parser,
                registry,
                new ThresholdEvaluator());

        List<FlowStructureValidationError> errors = validator.validate(flow(
                new NodeDefinition(1L, NodeType.SENSOR, new ObjectMapper().createObjectNode())));

        assertEquals(1, errors.size());
        assertEquals(FlowExecutableErrorCode.INVALID_NODE_CONFIGURATION, errors.getFirst().code());
    }

    @Test
    void unsupportedNodeExecutorPreventsActivation() {
        FlowStructureValidator structureValidator = mock(FlowStructureValidator.class);
        NodeParamsParser parser = new NodeParamsParser(
                new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator());
        FlowActivationValidator validator = new FlowActivationValidator(
                structureValidator,
                parser,
                new NodeExecutorRegistry(List.of()),
                new ThresholdEvaluator());

        List<FlowStructureValidationError> errors = validator.validate(flow(
                new NodeDefinition(1L, NodeType.ALERT, new ObjectMapper().createObjectNode())));

        assertTrue(errors.stream().anyMatch(error ->
                error.code() == FlowExecutableErrorCode.UNSUPPORTED_NODE_EXECUTOR));
        assertEquals("플로우를 활성화할 수 없습니다.", errors.getFirst().message());
    }

    @Test
    void legacyActuatorConfigurationCanBeActivated() {
        FlowStructureValidator structureValidator = mock(FlowStructureValidator.class);
        NodeExecutor executor = mock(NodeExecutor.class);
        when(executor.supports()).thenReturn(NodeType.ACTUATOR_CONTROL);
        NodeParamsParser parser = new NodeParamsParser(
                new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator());
        FlowActivationValidator validator = new FlowActivationValidator(
                structureValidator,
                parser,
                new NodeExecutorRegistry(List.of(executor)),
                new ThresholdEvaluator());

        List<FlowStructureValidationError> errors = validator.validate(flow(
                new NodeDefinition(
                        1L,
                        NodeType.ACTUATOR_CONTROL,
                        new ObjectMapper().createObjectNode().put("deviceId", 900L))));

        assertTrue(errors.isEmpty());
    }

    private FlowDefinition flow(NodeDefinition node) {
        return new FlowDefinition(
                1L,
                1L,
                1L,
                "테스트 Flow",
                null,
                FlowStatus.INACTIVE,
                OffsetDateTime.now(),
                List.of(node),
                List.of());
    }
}
