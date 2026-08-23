package com.nhnacademy.insightonruleengine.flow.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.api.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowNodeValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowNodeValidator.NodeRoleValidationResult;
import com.nhnacademy.insightonruleengine.flow.application.validation.NodeValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.NodeValidator.NodeValidationResult;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStructureErrorCode;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.domain.FlowValidationErrorReason;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlowNodeValidatorTest {

    private final NodeValidator nodeValidator = new NodeValidator();
    private final FlowNodeValidator flowNodeValidator = new FlowNodeValidator();

    @Test
    @DisplayName("트리거와 액션이 하나 이상 있으면 통과합니다.")
    void TriggerAndActionsTest() {
        NodeValidationResult nodes = nodeValidator.validate(List.of(
                node("trigger", NodeType.SENSOR),
                node("action1", NodeType.ALERT),
                node("action2", NodeType.EXTERNAL_NOTIFICATION)
        ));

        NodeRoleValidationResult actual = flowNodeValidator.validateRoles(nodes);

        assertTrue(actual.hasValidNodeRoles());
        assertTrue(actual.errors().isEmpty());
    }

    @Test
    @DisplayName("Trigger와 Action이 없으면 반환합니다.")
    void returnsTriggerAndActionTest() {
        NodeValidationResult nodes = nodeValidator.validate(
                List.of(node("filter", NodeType.THRESHOLD))
        );

        NodeRoleValidationResult actual = flowNodeValidator.validateRoles(nodes);

        assertEquals(List.of(
                FlowStructureErrorCode.INVALID_TRIGGER_COUNT,
                FlowStructureErrorCode.MISSING_ACTION
        ), errorCodes(actual.errors()));
        assertFalse(actual.hasValidNodeRoles());
    }

    @Test
    @DisplayName("Trigger가 하나여야 한다는 검증 테스트")
    void MultipleTriggersTest() {
        NodeValidationResult nodes = nodeValidator.validate(List.of(
                node("trigger1", NodeType.SENSOR),
                node("trigger2", NodeType.SCHEDULE),
                node("action", NodeType.ALERT)
        ));

        NodeRoleValidationResult actual = flowNodeValidator.validateRoles(nodes);

        assertEquals(List.of(FlowStructureErrorCode.INVALID_TRIGGER_COUNT), errorCodes(actual.errors()));
        assertFalse(actual.hasValidNodeRoles());
    }

    @Test
    @DisplayName("기본 Node 구조가 잘못되면 역할 검증을 건너뜁니다.")
    void skipsNodeTest() {
        NodeValidationResult nodes = nodeValidator.validate(
                List.of(FlowNodeRequest.builder().clientNodeKey("node").build())
        );

        NodeRoleValidationResult actual = flowNodeValidator.validateRoles(nodes);

        assertTrue(actual.errors().isEmpty());
        assertFalse(actual.hasValidNodeRoles());
    }

    private FlowNodeRequest node(String clientNodeKey, NodeType nodeType) {
        return FlowNodeRequest.builder()
                .clientNodeKey(clientNodeKey)
                .nodeType(nodeType)
                .configuration(JsonNodeFactory.instance.objectNode())
                .build();
    }

    private List<FlowValidationErrorReason> errorCodes(List<FlowStructureValidationError> errors) {
        return errors.stream()
                .map(FlowStructureValidationError::code)
                .toList();
    }
}
