package com.nhnacademy.insightonruleengine.runner.execution.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.runner.execution.executor.LocationNodeExecutor;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.execution.location.LocationMetricProcessor;
import jakarta.validation.Validation;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocationNodeExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecordingLocationMetricProcessor locationMetricProcessor = new RecordingLocationMetricProcessor();
    private final LocationNodeExecutor executor = new LocationNodeExecutor(
            new NodeParamsParser(objectMapper, Validation.buildDefaultValidatorFactory().getValidator()),
            locationMetricProcessor
    );

    @Test
    @DisplayName("LocationNodeExecutor는 빈 LocationParams를 파싱하고 공통 처리기를 실행한 뒤 out 포트로 진행한다")
    void executeLocationNode() {
        JsonNode configuration = objectMapper.createObjectNode();
        NodeDefinition node = new NodeDefinition(1L, NodeType.LOCATION, configuration);
        FlowExecutionContext context = new FlowExecutionContext(
                new FlowDefinition(
                        1L,
                        1L,
                        10L,
                        "location flow",
                        null,
                        FlowStatus.ACTIVE,
                        OffsetDateTime.now(),
                        List.of(node),
                        List.of()
                ),
                new SensorEvent(1L, 10L, 100L, Map.of("temperature", 25.1), Instant.now())
        );

        NodeExecutionResult result = executor.execute(node, context);

        assertEquals("out", result.outputPort());
        assertTrue(locationMetricProcessor.called);
        assertEquals(context, locationMetricProcessor.context);
    }

    private static class RecordingLocationMetricProcessor implements LocationMetricProcessor {

        private boolean called;
        private FlowExecutionContext context;

        @Override
        public void prepare(FlowExecutionContext context) {
            this.called = true;
            this.context = context;
        }
    }
}
