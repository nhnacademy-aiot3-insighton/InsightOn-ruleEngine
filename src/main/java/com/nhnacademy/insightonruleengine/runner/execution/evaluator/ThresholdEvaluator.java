package com.nhnacademy.insightonruleengine.runner.execution.evaluator;

import com.nhnacademy.insightonruleengine.runner.dto.FlowExecutionContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ThresholdEvaluator {

    private static final int MAX_EXPRESSION_LENGTH = 1000;
    private static final int MAX_CACHE_ENTRIES = 1024;

    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ConcurrentMap<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public boolean evaluate(String expressionText, FlowExecutionContext flowContext) {
        if (expressionText == null || expressionText.isBlank()) {
            throw new IllegalArgumentException("Threshold expression은 필수입니다.");
        }
        if (flowContext == null) {
            throw new IllegalArgumentException("flowContext는 필수입니다.");
        }

        Expression expression = parse(expressionText);
        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        context.setVariable("event", flowContext.event());
        context.setVariable("metrics", flowContext.metrics());

        Boolean result = expression.getValue(context, Boolean.class);
        if (result == null) {
            throw new IllegalArgumentException("Threshold expression은 boolean 결과여야 합니다.");
        }
        return result;
    }

    public void validateExpression(String expressionText) {
        if (expressionText == null || expressionText.isBlank()) {
            throw new IllegalArgumentException("Threshold expression은 필수입니다.");
        }
        parse(expressionText);
    }

    private Expression parse(String expressionText) {
        if (expressionText.length() > MAX_EXPRESSION_LENGTH) {
            throw new IllegalArgumentException("Threshold expression 길이가 허용 범위를 초과했습니다.");
        }
        Expression cached = expressionCache.get(expressionText);
        if (cached != null) {
            return cached;
        }
        Expression parsed = expressionParser.parseExpression(expressionText);
        if (expressionCache.size() >= MAX_CACHE_ENTRIES) {
            expressionCache.clear();
        }
        expressionCache.putIfAbsent(expressionText, parsed);
        return expressionCache.get(expressionText);
    }
}
