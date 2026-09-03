package com.nhnacademy.insightonruleengine.flow.application.validation;

import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowGraph;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowValidationErrorReason;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.LinkDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.ThresholdParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.runner.execution.evaluator.ThresholdEvaluator;
import com.nhnacademy.insightonruleengine.runner.execution.executor.NodeExecutorRegistry;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.expression.spel.SpelParseException;
import org.springframework.stereotype.Component;

/**
 * ACTIVE 전환 전에 저장된 Flow가 현재 엔진에서 실행 가능한지 확인한다.
 *
 * <p>그래프 규칙은 저장 전 요청과 같으므로 {@link FlowGraphValidator}를 그대로 쓰고, 여기서는 저장 시점에는
 * 알 수 없는 것 — 이 엔진에 실행기가 있는지, Threshold 식을 파싱할 수 있는지 — 만 덧붙인다.
 */
@Component
@RequiredArgsConstructor
public class FlowActivationValidator {

    private static final String NODE_FIELD_PREFIX = "nodes[";

    private final FlowGraphValidator flowGraphValidator;
    private final NodeParamsParser nodeParamsParser;
    private final NodeExecutorRegistry nodeExecutorRegistry;
    private final ThresholdEvaluator thresholdEvaluator;

    public List<FlowStructureValidationError> validate(FlowDefinition flow) {
        if (flow == null) {
            throw new IllegalArgumentException("flow는 필수입니다.");
        }

        List<FlowStructureValidationError> errors =
                new ArrayList<>(flowGraphValidator.validate(toGraph(flow)));
        for (NodeDefinition node : flow.nodes()) {
            validateNode(node, errors);
        }
        return List.copyOf(errors);
    }

    // 저장된 정의를 검증용 그래프로 옮긴다. 식별자는 nodeId, 오류 필드 경로는 Link 순서를 쓴다.
    private FlowGraph toGraph(FlowDefinition flow) {
        List<FlowGraph.Node> nodes = flow.nodes().stream()
                .map(node -> new FlowGraph.Node(node.nodeId().toString(), node.nodeType()))
                .toList();

        List<LinkDefinition> linkDefinitions = flow.links();
        List<FlowGraph.Link> links = new ArrayList<>();
        for (int index = 0; index < linkDefinitions.size(); index++) {
            LinkDefinition link = linkDefinitions.get(index);
            links.add(FlowGraph.Link.at(
                    index,
                    link.sourceNodeId().toString(),
                    link.sourcePort(),
                    link.targetNodeId().toString(),
                    link.targetPort()
            ));
        }
        return new FlowGraph(nodes, links);
    }

    private void validateNode(NodeDefinition node, List<FlowStructureValidationError> errors) {
        try {
            nodeExecutorRegistry.get(node.nodeType());
        } catch (RuntimeException exception) {
            errors.add(error(
                    FlowExecutableErrorCode.UNSUPPORTED_NODE_EXECUTOR,
                    node,
                    NODE_FIELD_PREFIX + node.nodeId() + "]",
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
                    NODE_FIELD_PREFIX + node.nodeId() + "].configuration.expression",
                    "Threshold expression 문법이 올바르지 않습니다."
            ));
        } catch (RuntimeException exception) {
            errors.add(error(
                    FlowExecutableErrorCode.INVALID_NODE_CONFIGURATION, node,
                    NODE_FIELD_PREFIX + node.nodeId() + "].configuration",
                    "Node configuration이 현재 엔진 계약에 맞지 않습니다."
            ));
        }
    }

    private FlowStructureValidationError error(
            FlowValidationErrorReason code,
            NodeDefinition node,
            String fieldPath,
            String message
    ) {
        return new FlowStructureValidationError(code, node.nodeId().toString(), fieldPath, message);
    }
}
