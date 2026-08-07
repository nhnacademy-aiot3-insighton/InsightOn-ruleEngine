package com.nhnacademy.insightonruleengine.flow.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.dto.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowNodeRequest;
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
}
