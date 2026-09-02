package com.nhnacademy.insightonruleengine.flow.application.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowLinkValidator.LinkReferenceResult;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowLinkValidator.LinkRulesResult;
import com.nhnacademy.insightonruleengine.flow.application.validation.LinkValidator.LinkValidationResult;
import com.nhnacademy.insightonruleengine.flow.application.validation.NodeValidator.NodeValidationResult;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureErrorCode;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowValidationErrorReason;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlowLinkValidatorTest {

    private final NodeValidator nodeValidator = new NodeValidator();
    private final LinkValidator linkValidator = new LinkValidator();
    private final FlowLinkValidator flowLinkValidator = new FlowLinkValidator();

    @Test
    @DisplayName("Trigger에서 나가는 정상 Link를 입력 Link로 거부하지 않는다")
    void triggerOutputTest() {
        FlowNodeRequest trigger = node("trigger", NodeType.SENSOR);
        FlowNodeRequest action = node("action", NodeType.ALERT);
        FlowLinkRequest link = link("trigger", "action", "out");

        LinkValidationResult linkResult = linkValidator.validate(List.of(link));
        LinkReferenceResult linkRefResult = flowLinkValidator.validateLinkReferences(linkResult,
                nodeMap(trigger, action));
        LinkRulesResult actual = flowLinkValidator.validateBusinessRules(linkRefResult, nodeMap(trigger, action));

        assertTrue(actual.errors().isEmpty());
    }

    @Test
    @DisplayName("Schedule Trigger는 Actuator Control에 직접 연결할 수 있다")
    void scheduleActuatorLinkTest() {
        FlowNodeRequest schedule = node("schedule", NodeType.SCHEDULE);
        FlowNodeRequest actuator = node("actuator", NodeType.ACTUATOR_CONTROL);
        FlowLinkRequest link = link("schedule", "actuator", "out");

        LinkValidationResult linkResult = linkValidator.validate(List.of(link));
        LinkReferenceResult linkRefResult = flowLinkValidator.validateLinkReferences(
                linkResult,
                nodeMap(schedule, actuator)
        );
        LinkRulesResult actual = flowLinkValidator.validateBusinessRules(
                linkRefResult,
                nodeMap(schedule, actuator)
        );

        assertTrue(actual.errors().isEmpty());
    }

    @Test
    @DisplayName("Schedule Trigger는 복수 Actuator Control로 fan-out할 수 있다")
    void scheduleActuatorFanOutTest() {
        FlowNodeRequest schedule = node("schedule", NodeType.SCHEDULE);
        FlowNodeRequest actuator1 = node("actuator1", NodeType.ACTUATOR_CONTROL);
        FlowNodeRequest actuator2 = node("actuator2", NodeType.ACTUATOR_CONTROL);
        FlowLinkRequest link1 = link("schedule", "actuator1", "out");
        FlowLinkRequest link2 = link("schedule", "actuator2", "out");

        LinkValidationResult linkResult = linkValidator.validate(List.of(link1, link2));
        LinkReferenceResult linkRefResult = flowLinkValidator.validateLinkReferences(
                linkResult,
                nodeMap(schedule, actuator1, actuator2)
        );
        LinkRulesResult actual = flowLinkValidator.validateBusinessRules(
                linkRefResult,
                nodeMap(schedule, actuator1, actuator2)
        );

        assertTrue(actual.errors().isEmpty());
        assertTrue(actual.canValidateConnections());
    }

    @Test
    @DisplayName("Schedule Trigger는 Alert에 연결할 수 없다")
    void rejectScheduleAlertLinkTest() {
        FlowNodeRequest schedule = node("schedule", NodeType.SCHEDULE);
        FlowNodeRequest alert = node("alert", NodeType.ALERT);
        FlowLinkRequest link = link("schedule", "alert", "out");

        LinkValidationResult linkResult = linkValidator.validate(List.of(link));
        LinkReferenceResult linkRefResult = flowLinkValidator.validateLinkReferences(
                linkResult,
                nodeMap(schedule, alert)
        );
        LinkRulesResult actual = flowLinkValidator.validateBusinessRules(
                linkRefResult,
                nodeMap(schedule, alert)
        );

        assertEquals(List.of(FlowStructureErrorCode.INVALID_SCHEDULE_TARGET), errorCodes(actual.errors()));
        assertFalse(actual.canValidateConnections());
    }

    @Test
    @DisplayName("Schedule Trigger는 Filter를 거쳐 Actuator Control에 연결할 수 없다")
    void rejectScheduleFilterLinkTest() {
        FlowNodeRequest schedule = node("schedule", NodeType.SCHEDULE);
        FlowNodeRequest filter = node("filter", NodeType.THRESHOLD);
        FlowLinkRequest link = link("schedule", "filter", "out");

        LinkValidationResult linkResult = linkValidator.validate(List.of(link));
        LinkReferenceResult linkRefResult = flowLinkValidator.validateLinkReferences(
                linkResult,
                nodeMap(schedule, filter)
        );
        LinkRulesResult actual = flowLinkValidator.validateBusinessRules(
                linkRefResult,
                nodeMap(schedule, filter)
        );

        assertEquals(List.of(FlowStructureErrorCode.INVALID_SCHEDULE_TARGET), errorCodes(actual.errors()));
        assertFalse(actual.canValidateConnections());
    }

    @Test
    @DisplayName("Trigger로 들어오는 Link만 Trigger 입력 오류로 거부한다")
    void triggerInputTest() {
        FlowNodeRequest filter = node("filter", NodeType.THRESHOLD);
        FlowNodeRequest trigger = node("trigger", NodeType.SENSOR);
        FlowLinkRequest link = link("filter", "trigger", "true");

        LinkValidationResult linkResult = linkValidator.validate(List.of(link));
        LinkReferenceResult linkRefResult = flowLinkValidator.validateLinkReferences(linkResult,
                nodeMap(filter, trigger));
        LinkRulesResult actual = flowLinkValidator.validateBusinessRules(linkRefResult, nodeMap(filter, trigger));

        assertEquals(List.of(FlowStructureErrorCode.TRIGGER_INPUT_LINK), errorCodes(actual.errors()));
        assertFalse(actual.canValidateConnections());
    }

    @Test
    @DisplayName("Self-loop 링크는 거부한다")
    void selfLoopTest() {
        FlowNodeRequest filter = node("filter", NodeType.THRESHOLD);
        FlowLinkRequest link = link("filter", "filter", "true");

        LinkValidationResult linkResult = linkValidator.validate(List.of(link));
        LinkReferenceResult linkRefResult = flowLinkValidator.validateLinkReferences(linkResult, nodeMap(filter));
        LinkRulesResult actual = flowLinkValidator.validateBusinessRules(linkRefResult, nodeMap(filter));

        assertEquals(List.of(FlowStructureErrorCode.SELF_LOOP), errorCodes(actual.errors()));
        assertFalse(actual.canValidateConnections());
    }

    @Test
    @DisplayName("동일 출력 포트에서 복수 Action으로 연결할 수 있다")
    void actionFanOutTest() {
        FlowNodeRequest trigger = node("trigger", NodeType.SENSOR);
        FlowNodeRequest action1 = node("action1", NodeType.ALERT);
        FlowNodeRequest action2 = node("action2", NodeType.ACTUATOR_CONTROL);
        FlowLinkRequest link1 = link("trigger", "action1", "out");
        FlowLinkRequest link2 = link("trigger", "action2", "out");

        LinkValidationResult linkResult = linkValidator.validate(List.of(link1, link2));
        LinkReferenceResult linkRefResult = flowLinkValidator.validateLinkReferences(linkResult,
                nodeMap(trigger, action1, action2));
        LinkRulesResult actual = flowLinkValidator.validateBusinessRules(linkRefResult,
                nodeMap(trigger, action1, action2));

        assertTrue(actual.errors().isEmpty());
        assertTrue(actual.canValidateConnections());
    }

    @Test
    @DisplayName("동일 출력 포트의 복수 링크에 비Action 대상이 포함되면 거부한다")
    void rejectNonActionFanOutTest() {
        FlowNodeRequest trigger = node("trigger", NodeType.SENSOR);
        FlowNodeRequest filter = node("filter", NodeType.THRESHOLD);
        FlowNodeRequest action = node("action", NodeType.ALERT);
        FlowLinkRequest link1 = link("trigger", "filter", "out");
        FlowLinkRequest link2 = link("trigger", "action", "out");

        LinkValidationResult linkResult = linkValidator.validate(List.of(link1, link2));
        LinkReferenceResult linkRefResult = flowLinkValidator.validateLinkReferences(
                linkResult,
                nodeMap(trigger, filter, action)
        );
        LinkRulesResult actual = flowLinkValidator.validateBusinessRules(
                linkRefResult,
                nodeMap(trigger, filter, action)
        );

        assertEquals(List.of(FlowStructureErrorCode.INVALID_FAN_OUT_TARGET), errorCodes(actual.errors()));
        assertEquals("links[0].targetClientNodeKey", actual.errors().getFirst().fieldPath());
        assertFalse(actual.canValidateConnections());
    }

    @Test
    @DisplayName("비Action 대상이 두 번째 fan-out 링크이면 두 번째 링크의 필드 경로를 반환한다")
    void rejectSecondNonActionFanOutWithExactFieldPathTest() {
        FlowNodeRequest trigger = node("trigger", NodeType.SENSOR);
        FlowNodeRequest action = node("action", NodeType.ALERT);
        FlowNodeRequest filter = node("filter", NodeType.THRESHOLD);
        FlowLinkRequest link1 = link("trigger", "action", "out");
        FlowLinkRequest link2 = link("trigger", "filter", "out");

        LinkValidationResult linkResult = linkValidator.validate(List.of(link1, link2));
        LinkReferenceResult linkRefResult = flowLinkValidator.validateLinkReferences(
                linkResult,
                nodeMap(trigger, action, filter)
        );
        LinkRulesResult actual = flowLinkValidator.validateBusinessRules(
                linkRefResult,
                nodeMap(trigger, action, filter)
        );

        assertEquals(List.of(FlowStructureErrorCode.INVALID_FAN_OUT_TARGET), errorCodes(actual.errors()));
        assertEquals("links[1].targetClientNodeKey", actual.errors().getFirst().fieldPath());
        assertFalse(actual.canValidateConnections());
    }

    @Test
    @DisplayName("출발·도착 Node와 Port가 모두 같은 링크는 중복 저장할 수 없다")
    void rejectDuplicateLinkTest() {
        FlowNodeRequest trigger = node("trigger", NodeType.SENSOR);
        FlowNodeRequest action = node("action", NodeType.ALERT);
        FlowLinkRequest link = link("trigger", "action", "out");

        LinkValidationResult linkResult = linkValidator.validate(List.of(link, link));
        LinkReferenceResult linkRefResult = flowLinkValidator.validateLinkReferences(
                linkResult,
                nodeMap(trigger, action)
        );
        LinkRulesResult actual = flowLinkValidator.validateBusinessRules(
                linkRefResult,
                nodeMap(trigger, action)
        );

        assertEquals(List.of(FlowStructureErrorCode.DUPLICATE_LINK), errorCodes(actual.errors()));
        assertFalse(actual.canValidateConnections());
    }

    @Test
    @DisplayName("존재하지 않는 노드를 가리키는 링크는 참조 오류로 거부한다")
    void missingNodeReferenceTest() {
        FlowNodeRequest trigger = node("trigger", NodeType.SENSOR);
        FlowLinkRequest link = link("trigger", "missing", "out");

        LinkValidationResult linkResult = linkValidator.validate(List.of(link));
        LinkReferenceResult linkRefResult = flowLinkValidator.validateLinkReferences(linkResult, nodeMap(trigger));

        assertEquals(List.of(FlowStructureErrorCode.MISSING_TARGET_NODE), errorCodes(linkRefResult.errors()));
        assertFalse(linkRefResult.canValidateConnections());
    }

    @Test
    @DisplayName("액션이 아닌 노드에 출력 링크가 없으면 거부한다")
    void missingOutputLinkTest() {
        FlowNodeRequest trigger = node("trigger", NodeType.SENSOR);
        FlowNodeRequest action = node("action", NodeType.ALERT);

        NodeValidationResult nodeResult = nodeValidator.validate(List.of(trigger, action));
        LinkReferenceResult linkRefResult = new LinkReferenceResult(List.of(), true, List.of());

        List<FlowStructureValidationError> errors = flowLinkValidator.validateMissingOutputLinks(
                nodeResult,
                linkRefResult,
                Set.of()
        );

        assertEquals(List.of(FlowStructureErrorCode.MISSING_OUTPUT_LINK), errorCodes(errors));
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
