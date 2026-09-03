package com.nhnacademy.insightonruleengine.flow.application.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowGraph;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureErrorCode;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowValidationErrorReason;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlowGraphValidatorTest {

    private final FlowGraphValidator validator = new FlowGraphValidator();

    // --- Node 역할 ---

    @Test
    @DisplayName("트리거와 액션이 하나 이상 있으면 역할 오류가 없습니다.")
    void triggerAndActionsTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(
                        node("trigger", NodeType.SENSOR),
                        node("action1", NodeType.ALERT),
                        node("action2", NodeType.ACTUATOR_CONTROL)
                ),
                List.of(
                        link(0, "trigger", "out", "action1"),
                        link(1, "trigger", "out", "action2")
                )
        ));

        assertTrue(errors.isEmpty(), "역할 오류가 없어야 합니다: " + errors);
    }

    @Test
    @DisplayName("Trigger와 Action이 없으면 두 오류를 함께 반환합니다.")
    void returnsTriggerAndActionTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(node("filter", NodeType.THRESHOLD)),
                List.of()
        ));

        assertTrue(errorCodes(errors).containsAll(List.of(
                FlowStructureErrorCode.INVALID_TRIGGER_COUNT,
                FlowStructureErrorCode.MISSING_ACTION
        )), "Trigger/Action 역할 오류가 있어야 합니다: " + errors);
    }

    @Test
    @DisplayName("Trigger가 둘이면 Trigger 수 오류를 반환합니다.")
    void multipleTriggersTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(
                        node("trigger1", NodeType.SENSOR),
                        node("trigger2", NodeType.SCHEDULE),
                        node("action", NodeType.ALERT)
                ),
                List.of(link(0, "trigger1", "out", "action"))
        ));

        assertHasCode(errors, FlowStructureErrorCode.INVALID_TRIGGER_COUNT);
    }

    @Test
    @DisplayName("Node 필수값이 잘못되면 역할과 연결 검증을 건너뜁니다.")
    void skipsGraphRulesWhenNodeFieldsInvalidTest() {
        List<FlowStructureValidationError> errors = validator.validate(
                new FlowGraph(List.of(node("node", null)), List.of()),
                false,
                true
        );

        assertTrue(errors.isEmpty(), "Node 필수값이 없으면 그래프 오류를 만들지 않아야 합니다: " + errors);
    }

    // --- Link 규칙 ---

    @Test
    @DisplayName("Trigger에서 나가는 정상 Link를 입력 Link로 거부하지 않는다")
    void triggerOutputTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(node("trigger", NodeType.SENSOR), node("action", NodeType.ALERT)),
                List.of(link(0, "trigger", "out", "action"))
        ));

        assertTrue(errors.isEmpty(), "정상 Trigger 출력 Link 검증 에러: " + errors);
    }

    @Test
    @DisplayName("Schedule Trigger는 Actuator Control에 직접 연결할 수 있다")
    void scheduleActuatorLinkTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(node("schedule", NodeType.SCHEDULE), node("actuator", NodeType.ACTUATOR_CONTROL)),
                List.of(link(0, "schedule", "out", "actuator"))
        ));

        assertTrue(errors.isEmpty(), "Schedule -> Actuator 검증 에러: " + errors);
    }

    @Test
    @DisplayName("Schedule Trigger는 복수 Actuator Control로 fan-out할 수 있다")
    void scheduleActuatorFanOutTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(
                        node("schedule", NodeType.SCHEDULE),
                        node("actuator1", NodeType.ACTUATOR_CONTROL),
                        node("actuator2", NodeType.ACTUATOR_CONTROL)
                ),
                List.of(
                        link(0, "schedule", "out", "actuator1"),
                        link(1, "schedule", "out", "actuator2")
                )
        ));

        assertTrue(errors.isEmpty(), "Schedule fan-out 검증 에러: " + errors);
    }

    @Test
    @DisplayName("Schedule Trigger는 Alert에 연결할 수 없다")
    void rejectScheduleAlertLinkTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(node("schedule", NodeType.SCHEDULE), node("alert", NodeType.ALERT)),
                List.of(link(0, "schedule", "out", "alert"))
        ));

        assertEquals(
                List.of(FlowStructureErrorCode.INVALID_SCHEDULE_TARGET),
                errorCodes(errors)
        );
    }

    @Test
    @DisplayName("Schedule Trigger는 Filter를 거쳐 Actuator Control에 연결할 수 없다")
    void rejectScheduleFilterLinkTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(node("schedule", NodeType.SCHEDULE), node("filter", NodeType.THRESHOLD)),
                List.of(link(0, "schedule", "out", "filter"))
        ));

        assertHasCode(errors, FlowStructureErrorCode.INVALID_SCHEDULE_TARGET);
    }

    @Test
    @DisplayName("Trigger로 들어오는 Link만 Trigger 입력 오류로 거부한다")
    void triggerInputTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(node("filter", NodeType.THRESHOLD), node("trigger", NodeType.SENSOR)),
                List.of(link(0, "filter", "true", "trigger"))
        ));

        assertHasCode(errors, FlowStructureErrorCode.TRIGGER_INPUT_LINK);
    }

    @Test
    @DisplayName("Self-loop 링크는 거부한다")
    void selfLoopTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(node("filter", NodeType.THRESHOLD)),
                List.of(link(0, "filter", "true", "filter"))
        ));

        assertHasCode(errors, FlowStructureErrorCode.SELF_LOOP);
    }

    @Test
    @DisplayName("동일 출력 포트에서 복수 Action으로 연결할 수 있다")
    void actionFanOutTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(
                        node("trigger", NodeType.SENSOR),
                        node("action1", NodeType.ALERT),
                        node("action2", NodeType.ACTUATOR_CONTROL)
                ),
                List.of(
                        link(0, "trigger", "out", "action1"),
                        link(1, "trigger", "out", "action2")
                )
        ));

        assertTrue(errors.isEmpty(), "Action fan-out 검증 에러: " + errors);
    }

    @Test
    @DisplayName("동일 출력 포트의 복수 링크에 비Action 대상이 포함되면 거부한다")
    void rejectNonActionFanOutTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(
                        node("trigger", NodeType.SENSOR),
                        node("filter", NodeType.THRESHOLD),
                        node("action", NodeType.ALERT)
                ),
                List.of(
                        link(0, "trigger", "out", "filter"),
                        link(1, "trigger", "out", "action")
                )
        ));

        assertEquals(
                "links[0].targetClientNodeKey",
                findError(errors, FlowStructureErrorCode.INVALID_FAN_OUT_TARGET).fieldPath()
        );
    }

    @Test
    @DisplayName("비Action 대상이 두 번째 fan-out 링크이면 두 번째 링크의 필드 경로를 반환한다")
    void rejectSecondNonActionFanOutWithExactFieldPathTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(
                        node("trigger", NodeType.SENSOR),
                        node("action", NodeType.ALERT),
                        node("filter", NodeType.THRESHOLD)
                ),
                List.of(
                        link(0, "trigger", "out", "action"),
                        link(1, "trigger", "out", "filter")
                )
        ));

        assertEquals(
                "links[1].targetClientNodeKey",
                findError(errors, FlowStructureErrorCode.INVALID_FAN_OUT_TARGET).fieldPath()
        );
    }

    @Test
    @DisplayName("출발·도착 Node와 Port가 모두 같은 링크는 중복 저장할 수 없다")
    void rejectDuplicateLinkTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(node("trigger", NodeType.SENSOR), node("action", NodeType.ALERT)),
                List.of(
                        link(0, "trigger", "out", "action"),
                        link(1, "trigger", "out", "action")
                )
        ));

        assertEquals(List.of(FlowStructureErrorCode.DUPLICATE_LINK), errorCodes(errors));
    }

    @Test
    @DisplayName("타겟 포트가 in이 아니면 거부한다")
    void rejectInvalidTargetPortTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(node("trigger", NodeType.SENSOR), node("action", NodeType.ALERT)),
                List.of(new FlowGraph.Link("links[0]", "trigger", "out", "action", "out"))
        ));

        assertEquals(
                "links[0].targetPort",
                findError(errors, FlowStructureErrorCode.INVALID_PORT).fieldPath()
        );
    }

    @Test
    @DisplayName("존재하지 않는 노드를 가리키는 링크는 참조 오류로 거부한다")
    void missingNodeReferenceTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(node("trigger", NodeType.SENSOR)),
                List.of(link(0, "trigger", "out", "missing"))
        ));

        FlowStructureValidationError error =
                findError(errors, FlowStructureErrorCode.MISSING_TARGET_NODE);
        assertEquals("links[0].targetClientNodeKey", error.fieldPath());
    }

    @Test
    @DisplayName("Link 필수값이 잘못되면 연결 검증을 건너뜁니다.")
    void skipsLinkRulesWhenLinkFieldsInvalidTest() {
        List<FlowStructureValidationError> errors = validator.validate(
                new FlowGraph(
                        List.of(node("trigger", NodeType.SENSOR), node("action", NodeType.ALERT)),
                        List.of()
                ),
                true,
                false
        );

        assertTrue(errors.isEmpty(), "Link 필수값이 없으면 연결 오류를 만들지 않아야 합니다: " + errors);
    }

    @Test
    @DisplayName("액션이 아닌 노드에 출력 링크가 없으면 거부한다")
    void missingOutputLinkTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(node("trigger", NodeType.SENSOR), node("action", NodeType.ALERT)),
                List.of()
        ));

        FlowStructureValidationError error =
                findError(errors, FlowStructureErrorCode.MISSING_OUTPUT_LINK);
        assertEquals("trigger", error.clientNodeKey());
    }

    @Test
    @DisplayName("Filter의 false 링크는 생략할 수 있지만 true 링크는 필요하다")
    void filterRequiresTrueOutputTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(
                        node("trigger", NodeType.LOCATION),
                        node("filter", NodeType.THRESHOLD),
                        node("action", NodeType.ALERT)
                ),
                List.of(
                        link(0, "trigger", "out", "filter"),
                        link(1, "filter", "false", "action")
                )
        ));

        FlowStructureValidationError error =
                findError(errors, FlowStructureErrorCode.MISSING_OUTPUT_LINK);
        assertEquals("filter", error.clientNodeKey());
    }

    // --- 경로 ---

    @Test
    @DisplayName("정상 경로이면 오류 없이 검증을 통과합니다.")
    void validPathTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(
                        node("trigger", NodeType.SENSOR),
                        node("filter", NodeType.THRESHOLD),
                        node("action", NodeType.ALERT)
                ),
                List.of(
                        link(0, "trigger", "out", "filter"),
                        link(1, "filter", "true", "action")
                )
        ));

        assertTrue(errors.isEmpty(), "정상 경로 검증 에러: " + errors);
    }

    @Test
    @DisplayName("Trigger에서 도달할 수 없는 Node가 있으면 오류를 반환합니다.")
    void unreachableNodeTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(
                        node("trigger", NodeType.SENSOR),
                        node("unreachable", NodeType.THRESHOLD),
                        node("action", NodeType.ALERT)
                ),
                List.of(link(0, "trigger", "out", "action"))
        ));

        assertEquals(
                "unreachable",
                findError(errors, FlowStructureErrorCode.UNREACHABLE_NODE).clientNodeKey()
        );
        assertEquals(
                "unreachable",
                findError(errors, FlowStructureErrorCode.CANNOT_REACH_ACTION).clientNodeKey()
        );
    }

    @Test
    @DisplayName("Action으로 이어지지 않는 경로가 있으면 오류를 반환합니다.")
    void cannotReachActionTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(
                        node("trigger", NodeType.SENSOR),
                        node("filter1", NodeType.THRESHOLD),
                        node("filter2", NodeType.THRESHOLD),
                        node("action", NodeType.ALERT)
                ),
                List.of(
                        link(0, "trigger", "out", "filter1"),
                        link(1, "filter1", "true", "action"),
                        link(2, "filter1", "false", "filter2")
                )
        ));

        assertEquals(
                "filter2",
                findError(errors, FlowStructureErrorCode.CANNOT_REACH_ACTION).clientNodeKey()
        );
    }

    @Test
    @DisplayName("Cycle 경로가 있으면 CYCLE_DETECTED 오류를 반환합니다.")
    void cycleDetectedTest() {
        List<FlowStructureValidationError> errors = validator.validate(new FlowGraph(
                List.of(
                        node("trigger", NodeType.SENSOR),
                        node("filter1", NodeType.THRESHOLD),
                        node("filter2", NodeType.THRESHOLD),
                        node("action", NodeType.ALERT)
                ),
                List.of(
                        link(0, "trigger", "out", "filter1"),
                        link(1, "filter1", "true", "filter2"),
                        link(2, "filter2", "true", "filter1"),
                        link(3, "filter2", "false", "action")
                )
        ));

        assertEquals(List.of(FlowStructureErrorCode.CYCLE_DETECTED), errorCodes(errors));
    }

    private FlowGraph.Node node(String key, NodeType nodeType) {
        return new FlowGraph.Node(key, nodeType);
    }

    private FlowGraph.Link link(int index, String source, String sourcePort, String target) {
        return FlowGraph.Link.at(index, source, sourcePort, target, "in");
    }

    private void assertHasCode(
            List<FlowStructureValidationError> errors,
            FlowStructureErrorCode code
    ) {
        assertTrue(
                errorCodes(errors).contains(code),
                code + " 오류가 있어야 합니다: " + errors
        );
    }

    private FlowStructureValidationError findError(
            List<FlowStructureValidationError> errors,
            FlowStructureErrorCode code
    ) {
        return errors.stream()
                .filter(error -> error.code() == code)
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError(code + " 오류가 있어야 합니다: " + errors));
    }

    private List<FlowValidationErrorReason> errorCodes(
            List<FlowStructureValidationError> errors
    ) {
        return errors.stream()
                .map(FlowStructureValidationError::code)
                .toList();
    }
}
