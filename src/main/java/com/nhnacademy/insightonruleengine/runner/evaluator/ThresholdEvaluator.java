package com.nhnacademy.insightonruleengine.runner.evaluator;

import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

@Component
public class ThresholdEvaluator {

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
        context.setVariable("metrics", event.metrics());

        Boolean result = expression.getValue(context, Boolean.class);
        if (result == null) {
            throw new IllegalArgumentException("Threshold expression은 boolean 결과여야 합니다.");
        }
        return result;
    }

}
