package com.nhnacademy.insightonruleengine.flow.domain.node.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.NodeParams;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NodeParamsParser {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    @SuppressWarnings("unchecked")
    public <T extends NodeParams> T parse(NodeType nodeType, JsonNode configuration) {
        if (nodeType == null) {
            throw new IllegalArgumentException("nodeType은 필수입니다.");
        }
        if (configuration == null || configuration.isNull()) {
            throw new IllegalArgumentException("configuration은 필수입니다.");
        }

        try {
//          public <T> T treeToValue(TreeNode n, Class<T> valueType)
//          ObjectMapper의 ReadTree와 tree to value의 적절한 사용이 필요함.

            T params = (T) objectMapper.treeToValue(configuration, nodeType.getParamsType());
            validate(params);
            return params;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Node configuration 파싱에 실패했습니다.", exception);
        }
    }

    private void validate(NodeParams params) {
        Set<ConstraintViolation<NodeParams>> violations = validator.validate(params);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

}
