package com.nhnacademy.insightonruleengine.runner.execution.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import jakarta.validation.Validation;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocationNodeExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LocationNodeExecutor executor = new LocationNodeExecutor(
            new NodeParamsParser(objectMapper, Validation.buildDefaultValidatorFactory().getValidator())
    );

    @Test
    @DisplayName("LocationNodeExecutor는 현재 장소의 센서 이벤트를 검증하고 out 포트로 진행한다")
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
    }

    @Test
    @DisplayName("Flow와 센서 이벤트의 장소가 다르면 실행을 거부한다")
    void rejectsAnotherLocation() {
        JsonNode configuration = objectMapper.createObjectNode();
        NodeDefinition node = new NodeDefinition(1L, NodeType.LOCATION, configuration);
        FlowDefinition flow = new FlowDefinition(
                1L, 1L, 10L, "location flow", null, FlowStatus.ACTIVE,
                OffsetDateTime.now(), List.of(node), List.of()
        );
        FlowExecutionContext context = new FlowExecutionContext(
                flow,
                new SensorEvent(1L, 20L, 100L, Map.of("temperature", 25.1), Instant.now())
        );

        assertThrows(IllegalArgumentException.class, () -> executor.execute(node, context));
    }
}
