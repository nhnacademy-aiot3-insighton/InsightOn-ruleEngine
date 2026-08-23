package com.nhnacademy.insightonruleengine.flow.application.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.application.validation.NodeValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.NodeValidator.NodeValidationResult;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowValidationErrorReason;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.NodeErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NodeValidatorTest {

    private final NodeValidator validator = new NodeValidator();

    @Test
    @DisplayName("Node 목록이 없으면 빈 목록 오류를 반환합니다.")
    void EmptyNodeListTest() {
        NodeValidationResult actual = validator.validate(null);

        assertEquals(List.of(NodeErrorCode.EMPTY_NODES), errorCodes(actual.errors()));
        assertTrue(actual.nodesByKey().isEmpty());
        assertFalse(actual.canValidateConnections());
    }

    @Test
    @DisplayName("null Node 요소의 요청 위치를 반환합니다.")
    void NullNodeElementTest() {
        List<FlowNodeRequest> nodes = new ArrayList<>();
        nodes.add(null);

        NodeValidationResult actual = validator.validate(nodes);

        assertEquals(List.of(NodeErrorCode.NULL_NODE), errorCodes(actual.errors()));
        assertEquals("nodes[0]", actual.errors().getFirst().fieldPath());
        assertFalse(actual.canValidateConnections());
    }

    @Test
    @DisplayName("Node 필수값 누락을 반환합니다.")
    void returnsErrorsNodeTest() {
        NodeValidationResult actual = validator.validate(
                List.of(FlowNodeRequest.builder().build())
        );

        assertEquals(List.of(
                NodeErrorCode.MISSING_CLIENT_NODE_KEY,
                NodeErrorCode.MISSING_NODE_TYPE,
                NodeErrorCode.MISSING_NODE_CONFIGURATION
        ), errorCodes(actual.errors()));
        assertEquals("nodes[0].clientNodeKey", actual.errors().get(0).fieldPath());
        assertEquals("nodes[0].nodeType", actual.errors().get(1).fieldPath());
        assertEquals("nodes[0].configuration", actual.errors().get(2).fieldPath());
        assertFalse(actual.canValidateConnections());
    }

    @Test
    @DisplayName("중복 clientNodeKey는 첫 Node를 보존후 오류 반환합니다.")
    void DuplicatedClientNodeKeyTest() {
        FlowNodeRequest first = node("same", NodeType.SENSOR);
        FlowNodeRequest duplicate = node("same", NodeType.SCHEDULE);

        NodeValidationResult actual = validator.validate(List.of(first, duplicate));

        assertEquals(List.of(NodeErrorCode.DUPLICATE_CLIENT_NODE_KEY), errorCodes(actual.errors()));
        assertEquals(first, actual.nodesByKey().get("same"));
        assertFalse(actual.canValidateConnections());
    }

    @Test
    @DisplayName("configuration 누락만으로는 연결 검증을 차단하지 않습니다.")
    void nonConfigurationTest() {
        FlowNodeRequest node = FlowNodeRequest.builder()
                .clientNodeKey("trigger")
                .nodeType(NodeType.SENSOR)
                .build();

        NodeValidationResult actual = validator.validate(List.of(node));

        assertEquals(List.of(NodeErrorCode.MISSING_NODE_CONFIGURATION), errorCodes(actual.errors()));
        assertTrue(actual.canValidateConnections());
    }

    @Test
    @DisplayName("JSON null configuration을 누락으로 반환합니다.")
    void JsonNullConfigurationTest() {
        FlowNodeRequest node = FlowNodeRequest.builder()
                .clientNodeKey("trigger")
                .nodeType(NodeType.SENSOR)
                .configuration(JsonNodeFactory.instance.nullNode())
                .build();

        NodeValidationResult actual = validator.validate(List.of(node));

        assertEquals(List.of(NodeErrorCode.MISSING_NODE_CONFIGURATION), errorCodes(actual.errors()));
        assertEquals("nodes[0].configuration", actual.errors().getFirst().fieldPath());
    }

    @Test
    @DisplayName("Node 조회 Map과 오류 목록은 외부에서 변경할 수 없습니다.")
    void returnsMapTest() {
        NodeValidationResult actual = validator.validate(
                List.of(node("trigger", NodeType.SENSOR))
        );

        Map<String, FlowNodeRequest> nodesByKey = actual.nodesByKey();
        FlowNodeRequest otherNode = node("other", NodeType.SENSOR);
        assertThrows(UnsupportedOperationException.class, () -> nodesByKey.put("other", otherNode));

        List<FlowStructureValidationError> errors = actual.errors();
        FlowStructureValidationError error = validationError();
        assertThrows(UnsupportedOperationException.class, () -> errors.add(error));
    }

    private FlowNodeRequest node(String clientNodeKey, NodeType nodeType) {
        return FlowNodeRequest.builder()
                .clientNodeKey(clientNodeKey)
                .nodeType(nodeType)
                .configuration(JsonNodeFactory.instance.objectNode())
                .build();
    }

    private FlowStructureValidationError validationError() {
        return new FlowStructureValidationError(
                NodeErrorCode.EMPTY_NODES,
                null,
                "nodes",
                "Node 목록은 비어 있을 수 없습니다."
        );
    }

    private List<FlowValidationErrorReason> errorCodes(List<FlowStructureValidationError> errors) {
        return errors.stream()
                .map(FlowStructureValidationError::code)
                .toList();
    }
}
