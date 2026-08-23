package com.nhnacademy.insightonruleengine.runner.execution.evaluator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhnacademy.insightonruleengine.runner.execution.evaluator.ThresholdEvaluator;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ThresholdEvaluatorTest {

    private final ThresholdEvaluator evaluator = new ThresholdEvaluator();

    @Test
    @DisplayName("metrics 값을 변수로 바인딩해 true를 반환한다")
    void evaluateTrueExpression() {
        FlowExecutionContext context = context(Map.of("temperature", 31.2, "humidity", 60));

        assertTrue(evaluator.evaluate("#metrics['temperature'] > 30", context));
    }

    @Test
    @DisplayName("metrics 값을 변수로 바인딩해 false를 반환한다")
    void evaluateFalseExpression() {
        FlowExecutionContext context = context(Map.of("temperature", 20.0, "humidity", 60));

        assertFalse(evaluator.evaluate("#metrics['temperature'] > 30", context));
    }

    @Test
    @DisplayName("event 필드를 expression에서 참조할 수 있다")
    void evaluateExpressionUsingEventVariable() {
        FlowExecutionContext context = context(Map.of("temperature", 31.2));

        assertTrue(evaluator.evaluate("#event.sensorId == 100", context));
    }

    @Test
    @DisplayName("event의 metrics 필드를 expression에서 참조할 수 있다")
    void evaluateExpressionUsingEventMetrics() {
        FlowExecutionContext context = context(Map.of("temperature", 31.2));

        assertTrue(evaluator.evaluate("#event.metrics['temperature'] > 30", context));
    }

    @Test
    @DisplayName("boolean이 아닌 expression 결과는 거부한다")
    void rejectNonBooleanExpression() {
        FlowExecutionContext context = context(Map.of("temperature", 20.0));

        assertThrows(RuntimeException.class, () -> evaluator.evaluate("#metrics['temperature']", context));
    }

    @Test
    @DisplayName("빈 expression은 거부한다")
    void rejectBlankExpression() {
        FlowExecutionContext context = context(Map.of("temperature", 20.0));

        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(" ", context));
    }

    @Test
    @DisplayName("null FlowExecutionContext는 거부한다")
    void rejectNullFlowContext() {
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate("#metrics['temperature'] > 30", null));
    }

    @Test
    @DisplayName("올바른 expression은 검증을 통과한다")
    void validateValidExpression() {
        assertDoesNotThrow(() -> evaluator.validateExpression("#metrics['temperature'] > 30"));
    }

    @Test
    @DisplayName("문법이 잘못된 expression은 검증 중 거부한다")
    void rejectInvalidExpressionSyntax() {
        assertThrows(RuntimeException.class, () -> evaluator.validateExpression("#metrics['temperature'] >"));
    }

    private FlowExecutionContext context(Map<String, Object> metrics) {
        return new FlowExecutionContext(flow(), event(metrics));
    }

    private FlowDefinition flow() {
        return new FlowDefinition(
                1L,
                1L,
                10L,
                "threshold flow",
                null,
                FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-03T00:00:00Z"),
                List.of(),
                List.of()
        );
    }

    private SensorEvent event(Map<String, Object> metrics) {
        return new SensorEvent(
                1L,
                10L,
                100L,
                metrics,
                Instant.parse("2026-08-03T00:00:00Z")
        );
    }
}
