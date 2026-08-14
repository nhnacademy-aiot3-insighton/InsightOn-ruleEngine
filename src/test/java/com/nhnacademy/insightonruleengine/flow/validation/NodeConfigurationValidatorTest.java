package com.nhnacademy.insightonruleengine.flow.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.flow.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.validation.domain.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.validation.domain.NodeErrorCode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NodeConfigurationValidatorTest {

    private final Validator beenValidator = Validation.buildDefaultValidatorFactory().getValidator();
    private final NodeConfigurationValidator validator = new NodeConfigurationValidator(
            new NodeParamsParser(new ObjectMapper(), beenValidator)
    );

    @Test
    @DisplayName("노드 타입에 맞는 configuration은 오류 없이 통과합니다.")
    void validConfigurationTest() {
        List<FlowNodeRequest> nodes = List.of(
                node("sensor", NodeType.SENSOR, JsonNodeFactory.instance.objectNode()
                        .put("devName", "temperature-sensor")),
                node("alert", NodeType.ALERT, JsonNodeFactory.instance.objectNode()
                        .put("title", "온도 경고")
                        .put("severity", "WARNING")
                        .put("message", "온도가 기준을 초과했습니다."))
        );
        assertTrue(validator.validate(nodes).isEmpty());
    }

    @Test
    @DisplayName("Bean Validation 오류는 노드 키와 configuration 필드 경로를 포함합니다.")
    void constraintViolationTest() {
        FlowNodeRequest sensor = node(
                "sensor",
                NodeType.SENSOR,
                JsonNodeFactory.instance.objectNode().put("devName", " ")
        );
        FlowStructureValidationError error = validator.validate(List.of(sensor)).getFirst();

        assertEquals(NodeErrorCode.INVALID_NODE_CONFIGURATION, error.code());
        assertEquals("sensor", error.clientNodeKey());
        assertEquals("nodes[0].configuration.devName", error.fieldPath());
    }

    @Test
    @DisplayName("노드 타입으로 변환할 수 없는 값은 configuration 전체 오류로 반환합니다.")
    void parsingFailureTest() {
        FlowNodeRequest alert = node(
                "alert",
                NodeType.ALERT,
                JsonNodeFactory.instance.objectNode()
                        .put("title", "온도 경고")
                        .put("severity", "UNKNOWN")
                        .put("message", "온도가 기준을 초과했습니다.")
        );
        FlowStructureValidationError error = validator.validate(List.of(alert)).getFirst();
        assertEquals(NodeErrorCode.INVALID_NODE_CONFIGURATION, error.code());
        assertEquals("alert", error.clientNodeKey());
        assertEquals("nodes[0].configuration", error.fieldPath());
    }

    @Test
    @DisplayName("기본 필드가 누락된 노드는 configuration 중복 오류를 만들지 않습니다.")
    void skipMissingTest() {
        FlowNodeRequest missing = FlowNodeRequest.builder()
                .clientNodeKey("sensor")
                .nodeType(NodeType.SENSOR)
                .configuration(null)
                .build();
        assertTrue(validator.validate(List.of(missing)).isEmpty());
    }

    private FlowNodeRequest node(
            String clientNodeKey,
            NodeType nodeType,
            JsonNode configuration
    ) {
        return FlowNodeRequest.builder()
                .clientNodeKey(clientNodeKey)
                .nodeType(nodeType)
                .configuration(configuration)
                .build();
    }
}