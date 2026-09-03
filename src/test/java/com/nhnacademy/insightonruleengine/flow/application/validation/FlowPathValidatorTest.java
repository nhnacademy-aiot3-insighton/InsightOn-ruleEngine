package com.nhnacademy.insightonruleengine.flow.application.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureErrorCode;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowValidationErrorReason;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlowPathValidatorTest {

    private final FlowPathValidator validator = new FlowPathValidator();

    @Test
    @DisplayName("정상 경로이면 오류 없이 검증을 통과합니다.")
    void validPathTest() {
        FlowNodeRequest trigger = node("trigger", NodeType.SENSOR);
        FlowNodeRequest filter = node("filter", NodeType.THRESHOLD);
        FlowNodeRequest action = node("action", NodeType.ALERT);

        Map<String, FlowNodeRequest> nodesByKey = nodeMap(trigger, filter, action);
        List<FlowLinkRequest> links = List.of(
                link("trigger", "filter", "out"),
                link("filter", "action", "true")
        );

        List<FlowStructureValidationError> errors = validator.validate(nodesByKey, links);

        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Trigger에서 도달할 수 없는 Node가 있으면 오류를 반환합니다.")
    void unreachableNodeTest() {
        FlowNodeRequest trigger = node("trigger", NodeType.SENSOR);
        FlowNodeRequest unreachable = node("unreachable", NodeType.THRESHOLD);
        FlowNodeRequest action = node("action", NodeType.ALERT);

        Map<String, FlowNodeRequest> nodesByKey = nodeMap(trigger, unreachable, action);
        List<FlowLinkRequest> links = List.of(
                link("trigger", "action", "out")
        );

        List<FlowStructureValidationError> errors = validator.validate(nodesByKey, links);

        assertEquals(List.of(FlowStructureErrorCode.UNREACHABLE_NODE, FlowStructureErrorCode.CANNOT_REACH_ACTION),
                errorCodes(errors));
        assertEquals("unreachable", errors.getFirst().clientNodeKey());
    }

    @Test
    @DisplayName("Action으로 이어지지 않는 경로가 있으면 오류를 반환합니다.")
    void cannotReachActionTest() {
        FlowNodeRequest trigger = node("trigger", NodeType.SENSOR);
        FlowNodeRequest filter1 = node("filter1", NodeType.THRESHOLD);
        FlowNodeRequest filter2 = node("filter2", NodeType.THRESHOLD);
        FlowNodeRequest action = node("action", NodeType.ALERT);

        Map<String, FlowNodeRequest> nodesByKey = nodeMap(trigger, filter1, filter2, action);
        List<FlowLinkRequest> links = List.of(
                link("trigger", "filter1", "out"),
                link("filter1", "action", "true"),
                link("filter1", "filter2", "false")
        );

        List<FlowStructureValidationError> errors = validator.validate(nodesByKey, links);

        assertEquals(List.of(FlowStructureErrorCode.CANNOT_REACH_ACTION), errorCodes(errors));
        assertEquals("filter2", errors.getFirst().clientNodeKey());
    }

    @Test
    @DisplayName("Cycle 경로가 있으면 CYCLE_DETECTED 오류를 반환합니다.")
    void cycleDetectedTest() {
        FlowNodeRequest trigger = node("trigger", NodeType.SENSOR);
        FlowNodeRequest filter1 = node("filter1", NodeType.THRESHOLD);
        FlowNodeRequest filter2 = node("filter2", NodeType.THRESHOLD);
        FlowNodeRequest action = node("action", NodeType.ALERT);

        Map<String, FlowNodeRequest> nodesByKey = nodeMap(trigger, filter1, filter2, action);
        List<FlowLinkRequest> links = List.of(
                link("trigger", "filter1", "out"),
                link("filter1", "filter2", "true"),
                link("filter2", "filter1", "true"),
                link("filter2", "action", "false")
        );

        List<FlowStructureValidationError> errors = validator.validate(nodesByKey, links);

        assertEquals(List.of(FlowStructureErrorCode.CYCLE_DETECTED), errorCodes(errors));
    }

    private FlowNodeRequest node(String clientNodeKey, NodeType nodeType) {
        return FlowNodeRequest.builder()
                .clientNodeKey(clientNodeKey)
                .nodeType(nodeType)
                .configuration(JsonNodeFactory.instance.objectNode())
                .build();
    }

    private Map<String, FlowNodeRequest> nodeMap(FlowNodeRequest... nodes) {
        Map<String, FlowNodeRequest> nodesByKey = new LinkedHashMap<>();
        for (FlowNodeRequest node : nodes) {
            nodesByKey.put(node.clientNodeKey(), node);
        }
        return nodesByKey;
    }

    private FlowLinkRequest link(String source, String target, String sourcePort) {
        return FlowLinkRequest.builder()
                .sourceClientNodeKey(source)
                .targetClientNodeKey(target)
                .sourcePort(sourcePort)
                .targetPort("in")
                .build();
    }

    private List<FlowValidationErrorReason> errorCodes(List<FlowStructureValidationError> errors) {
        return errors.stream()
                .map(FlowStructureValidationError::code)
                .toList();
    }
}
