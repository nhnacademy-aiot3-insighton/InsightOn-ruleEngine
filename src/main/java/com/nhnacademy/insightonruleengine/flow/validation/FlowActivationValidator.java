package com.nhnacademy.insightonruleengine.flow.validation;

import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.ThresholdParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.flow.dto.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.runner.evaluator.ThresholdEvaluator;
import com.nhnacademy.insightonruleengine.runner.executor.NodeExecutorRegistry;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.expression.spel.SpelParseException;

/** ACTIVE 전환 전에 저장된 Flow가 현재 엔진에서 실행 가능한지 확인합니다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowActivationValidator {

    private final FlowStructureValidator flowStructureValidator;
    private final NodeParamsParser nodeParamsParser;
    private final NodeExecutorRegistry nodeExecutorRegistry;
    private final ThresholdEvaluator thresholdEvaluator;

    public List<FlowStructureValidationError> validate(FlowDefinition flow) {
        if (flow == null) {
            throw new IllegalArgumentException("flow는 필수입니다.");
        }

        List<FlowStructureValidationError> errors = new ArrayList<>();
        List<FlowStructureValidationError> structureErrors = flowStructureValidator.validate(
                toNodeRequests(flow),
                toLinkRequests(flow));
        if (structureErrors != null) {
            errors.addAll(structureErrors);
        }
        for (NodeDefinition node : flow.nodes()) {
            validateNode(flow.flowId(), node, errors);
        }
        return List.copyOf(errors);
    }

    private void validateNode(Long flowId, NodeDefinition node, List<FlowStructureValidationError> errors) {
        try {
            nodeExecutorRegistry.get(node.nodeType());
        } catch (RuntimeException exception) {
            log.error(
                    "Flow 활성화 검증 중 Node Executor를 찾지 못했습니다. flowId={}, nodeId={}, nodeType={}", flowId, node.nodeId(), node.nodeType(), exception);

            errors.add(error(
                    FlowExecutableErrorCode.UNSUPPORTED_NODE_EXECUTOR,
                    node,
                    "nodes[" + node.nodeId() + "]",
                    "플로우를 활성화할 수 없습니다."
            ));
            return;
        }

        try {
            Object params = nodeParamsParser.parse(node.nodeType(), node.configuration());
            if (node.nodeType() == NodeType.THRESHOLD) {
                thresholdEvaluator.validateExpression(((ThresholdParams) params).expression());
            }
        } catch (SpelParseException exception) {
            errors.add(error(
                    FlowExecutableErrorCode.INVALID_THRESHOLD_EXPRESSION, node,
                    "nodes[" + node.nodeId() + "].configuration.expression",
                    "Threshold expression 문법이 올바르지 않습니다."
            ));
        } catch (RuntimeException exception) {
            errors.add(error(
                    FlowExecutableErrorCode.INVALID_NODE_CONFIGURATION, node,
                    "nodes[" + node.nodeId() + "].configuration",
                    "Node configuration이 현재 엔진 계약에 맞지 않습니다."
            ));
        }
    }

    private List<FlowNodeRequest> toNodeRequests(FlowDefinition flow) {
        return flow.nodes().stream()
                .map(node -> FlowNodeRequest.builder()
                        .clientNodeKey(node.nodeId().toString())
                        .nodeType(node.nodeType())
                        .configuration(node.configuration())
                        .build())
                .toList();
    }

    private List<FlowLinkRequest> toLinkRequests(FlowDefinition flow) {
        return flow.links().stream()
                .map(link -> FlowLinkRequest.builder()
                        .sourceClientNodeKey(link.sourceNodeId().toString())
                        .targetClientNodeKey(link.targetNodeId().toString())
                        .sourcePort(link.sourcePort())
                        .targetPort(link.targetPort())
                        .build())
                .toList();
    }

    private FlowStructureValidationError error(FlowValidationErrorReason code, NodeDefinition node, String fieldPath, String message) {
        return new FlowStructureValidationError(code, node.nodeId().toString(), fieldPath, message);
    }
}
