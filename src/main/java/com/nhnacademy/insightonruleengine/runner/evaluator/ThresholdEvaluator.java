package com.nhnacademy.insightonruleengine.runner.evaluator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ThresholdEvaluator {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private final ObjectMapper objectMapper;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    public boolean evaluate(String expressionText, SensorEvent event) {
        if (expressionText == null || expressionText.isBlank()) {
            throw new IllegalArgumentException("Threshold expression은 필수입니다.");
        }
        if (event == null) {
            throw new IllegalArgumentException("event는 필수입니다.");
        }

        Expression expression = expressionParser.parseExpression(expressionText);
        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        context.setVariable("event", event);
        context.setVariable("payload", event.payload());
        bindPayloadVariables(context, event.payload());

        Boolean result = expression.getValue(context, Boolean.class);
        if (result == null) {
            throw new IllegalArgumentException("Threshold expression은 boolean 결과여야 합니다.");
        }
        return result;
    }

    private void bindPayloadVariables(SimpleEvaluationContext context, JsonNode payload) {
        if (payload == null || payload.isNull() || !payload.isObject()) {
            return;
        }
        Map<String, Object> values = objectMapper.convertValue(payload, MAP_TYPE);
        values.forEach(context::setVariable);
    }
}
