package com.nhnacademy.insightonruleengine.flow.application.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowRequestFieldValidator.Result;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowGraph;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowValidationErrorReason;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.LinkErrorCode;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.NodeErrorCode;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlowRequestFieldValidatorTest {

    private final FlowRequestFieldValidator validator = new FlowRequestFieldValidator();

    @Test
    @DisplayName("Node 목록이 없으면 빈 목록 오류를 반환합니다.")
    void emptyNodeListTest() {
        Result actual = validator.validate(null, links());

        assertEquals(List.of(NodeErrorCode.EMPTY_NODES), nodeErrorCodes(actual));
        assertTrue(actual.graph().nodes().isEmpty());
        assertFalse(actual.nodeFieldsValid());
    }

    @Test
    @DisplayName("null Node 요소의 요청 위치를 반환합니다.")
    void nullNodeElementTest() {
        List<FlowNodeRequest> nodes = new ArrayList<>();
        nodes.add(null);

        Result actual = validator.validate(nodes, links());

        assertEquals(List.of(NodeErrorCode.NULL_NODE), nodeErrorCodes(actual));
        assertEquals("nodes[0]", nodeErrors(actual).getFirst().fieldPath());
        assertFalse(actual.nodeFieldsValid());
    }

    @Test
    @DisplayName("Node 필수값 누락을 반환합니다.")
    void returnsErrorsNodeTest() {
        Result actual = validator.validate(
                List.of(FlowNodeRequest.builder().build()),
                links()
        );

        assertEquals(List.of(
                NodeErrorCode.MISSING_CLIENT_NODE_KEY,
                NodeErrorCode.MISSING_NODE_TYPE,
                NodeErrorCode.MISSING_NODE_CONFIGURATION
        ), nodeErrorCodes(actual));
        List<FlowStructureValidationError> errors = nodeErrors(actual);
        assertEquals("nodes[0].clientNodeKey", errors.get(0).fieldPath());
        assertEquals("nodes[0].nodeType", errors.get(1).fieldPath());
        assertEquals("nodes[0].configuration", errors.get(2).fieldPath());
        assertFalse(actual.nodeFieldsValid());
    }

    @Test
    @DisplayName("중복 clientNodeKey는 첫 Node를 보존후 오류 반환합니다.")
    void duplicatedClientNodeKeyTest() {
        FlowNodeRequest first = node("same", NodeType.SENSOR);
        FlowNodeRequest duplicate = node("same", NodeType.SCHEDULE);

        Result actual = validator.validate(List.of(first, duplicate), links());

        assertEquals(List.of(NodeErrorCode.DUPLICATE_CLIENT_NODE_KEY), nodeErrorCodes(actual));
        assertEquals(
                NodeType.SENSOR,
                actual.graph().nodesByKey().get("same").nodeType(),
                "중복 key에서는 먼저 온 Node가 남아야 한다"
        );
        assertFalse(actual.nodeFieldsValid());
    }

    @Test
    @DisplayName("configuration 누락만으로는 그래프 검증을 차단하지 않습니다.")
    void nonConfigurationTest() {
        FlowNodeRequest node = FlowNodeRequest.builder()
                .clientNodeKey("trigger")
                .nodeType(NodeType.SENSOR)
                .build();

        Result actual = validator.validate(List.of(node), links());

        assertEquals(List.of(NodeErrorCode.MISSING_NODE_CONFIGURATION), nodeErrorCodes(actual));
        assertTrue(actual.nodeFieldsValid());
    }

    @Test
    @DisplayName("JSON null configuration을 누락으로 반환합니다.")
    void jsonNullConfigurationTest() {
        FlowNodeRequest node = FlowNodeRequest.builder()
                .clientNodeKey("trigger")
                .nodeType(NodeType.SENSOR)
                .configuration(JsonNodeFactory.instance.nullNode())
                .build();

        Result actual = validator.validate(List.of(node), links());

        assertEquals(List.of(NodeErrorCode.MISSING_NODE_CONFIGURATION), nodeErrorCodes(actual));
        assertEquals("nodes[0].configuration", nodeErrors(actual).getFirst().fieldPath());
    }

    @Test
    @DisplayName("Link 목록이 없으면 빈 목록 오류를 반환합니다.")
    void emptyLinkListTest() {
        Result actual = validator.validate(List.of(node("trigger", NodeType.SENSOR)), null);

        assertEquals(List.of(LinkErrorCode.EMPTY_LINKS), linkErrorCodes(actual));
        assertTrue(actual.graph().links().isEmpty());
        assertFalse(actual.linkFieldsValid());
    }

    @Test
    @DisplayName("null Link 요소의 요청 위치를 반환합니다.")
    void nullLinkTest() {
        List<FlowLinkRequest> links = new ArrayList<>();
        links.add(null);

        Result actual = validator.validate(List.of(node("trigger", NodeType.SENSOR)), links);

        assertEquals(List.of(LinkErrorCode.NULL_LINK), linkErrorCodes(actual));
        assertEquals("links[0]", linkErrors(actual).getFirst().fieldPath());
        assertFalse(actual.linkFieldsValid());
    }

    @Test
    @DisplayName("링크 필수값 누락을 반환합니다.")
    void returnsErrorsLinkTest() {
        FlowLinkRequest link = FlowLinkRequest.builder()
                .sourceClientNodeKey("trigger")
                .targetClientNodeKey("action")
                .build();

        Result actual = validator.validate(
                List.of(node("trigger", NodeType.SENSOR)),
                List.of(link)
        );

        assertEquals(
                List.of(LinkErrorCode.MISSING_SOURCE_PORT, LinkErrorCode.MISSING_TARGET_PORT),
                linkErrorCodes(actual)
        );
        assertTrue(actual.graph().links().isEmpty());
        assertFalse(actual.linkFieldsValid());
    }

    @Test
    @DisplayName("필수값을 모두 갖춘 요청은 그래프로 옮겨집니다.")
    void buildsGraphTest() {
        Result actual = validator.validate(
                List.of(node("trigger", NodeType.SENSOR), node("action", NodeType.ALERT)),
                List.of(link("trigger", "action", "out"))
        );

        assertTrue(actual.errors().isEmpty());
        assertTrue(actual.nodeFieldsValid());
        assertTrue(actual.linkFieldsValid());
        assertEquals(
                List.of(
                        new FlowGraph.Node("trigger", NodeType.SENSOR),
                        new FlowGraph.Node("action", NodeType.ALERT)
                ),
                actual.graph().nodes()
        );
        assertEquals(
                List.of(new FlowGraph.Link("links[0]", "trigger", "out", "action", "in")),
                actual.graph().links()
        );
    }

    @Test
    @DisplayName("Node 조회 Map과 오류 목록은 외부에서 변경할 수 없습니다.")
    void returnsImmutableResultTest() {
        Result actual = validator.validate(
                List.of(node("trigger", NodeType.SENSOR)),
                links()
        );

        Map<String, FlowGraph.Node> nodesByKey = actual.graph().nodesByKey();
        FlowGraph.Node otherNode = new FlowGraph.Node("other", NodeType.SENSOR);
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

    private FlowLinkRequest link(String source, String target, String sourcePort) {
        return FlowLinkRequest.builder()
                .sourceClientNodeKey(source)
                .targetClientNodeKey(target)
                .sourcePort(sourcePort)
                .targetPort("in")
                .build();
    }

    // Node 검증만 보는 테스트가 Link 쪽 EMPTY_LINKS에 흔들리지 않도록 최소한의 유효 Link를 함께 넘긴다.
    private List<FlowLinkRequest> links() {
        return List.of(link("trigger", "action", "out"));
    }

    private FlowStructureValidationError validationError() {
        return new FlowStructureValidationError(
                NodeErrorCode.EMPTY_NODES,
                null,
                "nodes",
                "Node 목록은 비어 있을 수 없습니다."
        );
    }

    private List<FlowStructureValidationError> nodeErrors(Result result) {
        return result.errors().stream()
                .filter(error -> error.code() instanceof NodeErrorCode)
                .toList();
    }

    private List<FlowStructureValidationError> linkErrors(Result result) {
        return result.errors().stream()
                .filter(error -> error.code() instanceof LinkErrorCode)
                .toList();
    }

    private List<FlowValidationErrorReason> nodeErrorCodes(Result result) {
        return nodeErrors(result).stream()
                .map(FlowStructureValidationError::code)
                .toList();
    }

    private List<FlowValidationErrorReason> linkErrorCodes(Result result) {
        return linkErrors(result).stream()
                .map(FlowStructureValidationError::code)
                .toList();
    }
}
