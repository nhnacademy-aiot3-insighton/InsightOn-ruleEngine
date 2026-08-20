package com.nhnacademy.insightonruleengine.flow.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.FlowTestData;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.dto.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.validation.domain.FlowStructureErrorCode;
import com.nhnacademy.insightonruleengine.flow.validation.domain.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.validation.domain.FlowValidationErrorReason;
import com.nhnacademy.insightonruleengine.flow.validation.domain.LinkErrorCode;
import com.nhnacademy.insightonruleengine.flow.validation.domain.NodeErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlowStructureValidatorTest {

    private final NodeValidator nodeValidator = new NodeValidator();
    private final LinkValidator linkValidator = new LinkValidator();
    private final FlowNodeValidator flowNodeValidator = new FlowNodeValidator();
    private final FlowLinkValidator flowLinkValidator = new FlowLinkValidator();
    private final FlowPathValidator flowPathValidator = new FlowPathValidator();

    private final FlowStructureValidator validator = new FlowStructureValidator(
            nodeValidator,
            linkValidator,
            flowNodeValidator,
            flowLinkValidator,
            flowPathValidator
    );

    @Test
    @DisplayName("유효한 Flow 구성 요청은 검증 오류 없이 통과합니다.")
    void validFlowStructureTest() {
        FlowNodeRequest trigger = node("trigger", NodeType.SENSOR);
        FlowNodeRequest filter = node("filter", NodeType.THRESHOLD);
        FlowNodeRequest action = node("action", NodeType.ALERT);

        List<FlowNodeRequest> nodes = List.of(trigger, filter, action);
        List<FlowLinkRequest> links = List.of(
                link("trigger", "filter", "out"),
                link("filter", "action", "true")
        );

        List<FlowStructureValidationError> errors = validator.validate(nodes, links);

        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Node와 Link가 모두 없으면 Element 에러들을 통합 수집합니다.")
    void emptyNodesAndLinksTest() {
        List<FlowStructureValidationError> errors = validator.validate(null, null);

        assertEquals(
                List.of(NodeErrorCode.EMPTY_NODES, LinkErrorCode.EMPTY_LINKS),
                errorCodes(errors)
        );
    }

    @Test
    @DisplayName("NodeType이 누락된 노드가 링크에 포함되어도 검증 오류를 반환합니다.")
    void missingNodeTypeInLinkTest() {
        FlowNodeRequest source = node("source", null);
        FlowNodeRequest action = node("action", NodeType.ALERT);
        FlowLinkRequest link = link("source", "action", "out");

        List<FlowStructureValidationError> errors = validator.validate(
                List.of(source, action),
                List.of(link)
        );

        assertEquals(List.of(NodeErrorCode.MISSING_NODE_TYPE), errorCodes(errors));
    }

    @Test
    @DisplayName("Target NodeType이 누락된 링크도 검증 오류를 반환합니다.")
    void missingTargetNodeTypeInLinkTest() {
        FlowNodeRequest trigger = node("trigger", NodeType.SENSOR);
        FlowNodeRequest target = node("target", null);
        FlowLinkRequest link = link("trigger", "target", "out");

        List<FlowStructureValidationError> errors = validator.validate(
                List.of(trigger, target),
                List.of(link)
        );

        assertEquals(List.of(NodeErrorCode.MISSING_NODE_TYPE), errorCodes(errors));
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

    private List<FlowValidationErrorReason> errorCodes(List<FlowStructureValidationError> errors) {
        return errors.stream()
                .map(FlowStructureValidationError::code)
                .toList();
    }

    @Test
    @DisplayName("사전 구성 1: 온도 30도 이상 경보 플로우가 구조 검증을 통과한다")
    void temperatureThreshold30FlowTest() {
        var request = FlowTestData.createTemperatureThreshold30FlowRequest(
                10L);
        List<FlowStructureValidationError> errors = validator.validate(request.nodes(), request.links());
        assertTrue(errors.isEmpty(), "온도 30도 경보 플로우 검증 에러: " + errors);
    }

    @Test
    @DisplayName("Filter의 false 링크는 생략할 수 있지만 true 링크는 필요하다")
    void filterRequiresTrueOutputTest() {
        List<FlowNodeRequest> nodes = List.of(
                node("trigger", NodeType.LOCATION),
                node("filter", NodeType.THRESHOLD),
                node("action", NodeType.ALERT)
        );
        List<FlowLinkRequest> links = List.of(
                link("trigger", "filter", "out"),
                link("filter", "action", "false")
        );

        List<FlowStructureValidationError> errors = validator.validate(nodes, links);

        assertTrue(errors.stream().anyMatch(error ->
                        error.code() == FlowStructureErrorCode.MISSING_OUTPUT_LINK
                                && "filter".equals(error.clientNodeKey())),
                "Filter의 true 링크가 없으면 검증 오류가 있어야 합니다: " + errors);
    }

    @Test
    @DisplayName("사전 구성 2: 정기 환기 장치 구동 플로우가 구조 검증을 통과한다")
    void scheduledActuatorFlowTest() {
        var request = FlowTestData.createScheduledActuatorFlowRequest(10L);
        List<FlowStructureValidationError> errors = validator.validate(request.nodes(), request.links());
        assertTrue(errors.isEmpty(), "정기 환기 플로우 검증 에러: " + errors);
    }

    @Test
    @DisplayName("사전 구성 4: 이중 조건 직렬 검사 플로우가 구조 검증을 통과한다")
    void multiThresholdSerialFlowTest() {
        var request = FlowTestData.createMultiThresholdSerialFlowRequest(
                10L);
        List<FlowStructureValidationError> errors = validator.validate(request.nodes(), request.links());
        assertTrue(errors.isEmpty(), "이중 직렬 검사 플로우 검증 에러: " + errors);
    }

    @Test
    @DisplayName("사전 구성 5: 참/거짓 이중 분기 플로우가 구조 검증을 통과한다")
    void branchingTrueFalseFlowTest() {
        var request = FlowTestData.createTrueFalseFlowRequest(10L);
        List<FlowStructureValidationError> errors = validator.validate(request.nodes(), request.links());
        assertTrue(errors.isEmpty(), "이중 분기 플로우 검증 에러: " + errors);
    }

    @Test
    @DisplayName("사전 구성 6: 순환 구조 오류 플로우는 구조 검증에서 오류를 검출한다")
    void cyclicInvalidFlowTest() {
        var request = FlowTestData.createCyclicInvalidFlowRequest(10L);
        List<FlowStructureValidationError> errors = validator.validate(request.nodes(), request.links());
        assertTrue(
                errors.stream().anyMatch(error -> error.code() == FlowStructureErrorCode.CYCLE_DETECTED),
                "Cycle 오류가 검출되어야 합니다: " + errors
        );
    }


}
