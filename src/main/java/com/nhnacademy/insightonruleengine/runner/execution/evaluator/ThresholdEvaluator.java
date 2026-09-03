package com.nhnacademy.insightonruleengine.runner.execution.evaluator;

import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

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
        InvalidMetricTracker invalidMetricTracker = new InvalidMetricTracker();
        Map<String, Object> metrics = new NumericMetricsView(
                flowContext.metrics(),
                invalidMetricTracker
        );
        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        context.setVariable("event", evaluationEvent(flowContext.event(), metrics));
        context.setVariable("metrics", metrics);

        Boolean result;
        try {
            result = expression.getValue(context, Boolean.class);
        } catch (EvaluationException exception) {
            if (invalidMetricTracker.hasInvalidMetric()) {
                return false;
            }
            throw exception;
        }
        if (invalidMetricTracker.hasInvalidMetric()) {
            return false;
        }
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

    private EvaluationEvent evaluationEvent(
            SensorEvent event,
            Map<String, Object> metrics
    ) {
        if (event == null) {
            return null;
        }
        return new EvaluationEvent(
                event.groupId(),
                event.locationId(),
                event.sensorId(),
                metrics,
                event.timestamp()
        );
    }

    private record EvaluationEvent(
            Long groupId,
            Long locationId,
            Long sensorId,
            Map<String, Object> metrics,
            Instant timestamp
    ) {
    }

    private static final class NumericMetricsView extends AbstractMap<String, Object> {

        private final Map<String, Object> metrics;
        private final InvalidMetricTracker invalidMetricTracker;

        private NumericMetricsView(
                Map<String, Object> metrics,
                InvalidMetricTracker invalidMetricTracker
        ) {
            this.metrics = metrics;
            this.invalidMetricTracker = invalidMetricTracker;
        }

        @Override
        public Object get(Object key) {
            return validatedValue(metrics.get(key));
        }

        @Override
        public boolean containsKey(Object key) {
            boolean containsKey = metrics.containsKey(key);
            if (!containsKey) {
                invalidMetricTracker.markInvalid();
                return false;
            }
            validatedValue(metrics.get(key));
            return true;
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public Iterator<Entry<String, Object>> iterator() {
                    Iterator<Entry<String, Object>> iterator = metrics.entrySet().iterator();
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return iterator.hasNext();
                        }

                        @Override
                        public Entry<String, Object> next() {
                            Entry<String, Object> entry = iterator.next();
                            return new SimpleImmutableEntry<>(
                                    entry.getKey(),
                                    validatedValue(entry.getValue())
                            );
                        }
                    };
                }

                @Override
                public int size() {
                    return metrics.size();
                }
            };
        }

        private Object validatedValue(Object value) {
            if (!isValidNumber(value)) {
                invalidMetricTracker.markInvalid();
                return null;
            }
            return value;
        }

        private boolean isValidNumber(Object value) {
            if (!(value instanceof Number number)) {
                return false;
            }
            if (number instanceof Double doubleValue) {
                return Double.isFinite(doubleValue);
            }
            if (number instanceof Float floatValue) {
                return Float.isFinite(floatValue);
            }
            return true;
        }

        // AbstractMap의 Map 계약 기반 equals/hashCode(entrySet 비교)가 이미 metrics 내용을
        // 정확히 반영하므로, 추가 필드를 무시하는 게 아니라 의도적으로 그대로 상속받습니다.
        @Override
        public boolean equals(Object other) {
            return super.equals(other);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }

    private static final class InvalidMetricTracker {

        private boolean invalidMetric;

        private void markInvalid() {
            invalidMetric = true;
        }

        private boolean hasInvalidMetric() {
            return invalidMetric;
        }
    }
}
