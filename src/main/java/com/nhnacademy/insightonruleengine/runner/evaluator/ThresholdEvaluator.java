package com.nhnacademy.insightonruleengine.runner.evaluator;

import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.dto.FlowExecutionContext;
import java.util.Map;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

@Component
public class ThresholdEvaluator {

    private final ExpressionParser expressionParser = new SpelExpressionParser();

    public boolean evaluate(String expressionText, SensorEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event는 필수입니다.");
        }
        return evaluate(expressionText, event, event.metrics());
    }

    public boolean evaluate(String expressionText, FlowExecutionContext executionContext) {
        if (executionContext == null) {
            throw new IllegalArgumentException("context는 필수입니다.");
        }
        return evaluate(expressionText, executionContext.event(), executionContext.metrics());
    }

    private boolean evaluate(String expressionText, SensorEvent event, Map<String, Object> metrics) {
        if (expressionText == null || expressionText.isBlank()) {
            throw new IllegalArgumentException("Threshold expression은 필수입니다.");
        }
        if (event == null || metrics == null) {
            throw new IllegalArgumentException("event와 metrics는 필수입니다.");
        }

        Expression expression = expressionParser.parseExpression(expressionText);
        SimpleEvaluationContext evaluationContext = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        evaluationContext.setVariable("event", event);
        evaluationContext.setVariable("metrics", metrics);

        Boolean result = expression.getValue(evaluationContext, Boolean.class);
        if (result == null) {
            throw new IllegalArgumentException("Threshold expression은 boolean 결과여야 합니다.");
        }
        return result;
    }

    public void validateExpression(String expressionText) {
        if (expressionText == null || expressionText.isBlank()) {
            throw new IllegalArgumentException("Threshold expression은 필수입니다.");
        }
        expressionParser.parseExpression(expressionText);
    }

}
