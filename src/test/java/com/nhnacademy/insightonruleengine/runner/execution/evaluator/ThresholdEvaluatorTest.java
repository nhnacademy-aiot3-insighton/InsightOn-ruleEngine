package com.nhnacademy.insightonruleengine.runner.execution.evaluator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "#metrics['humidity'] > 50",
            "#metrics['humidity'] >= 50",
            "#metrics['humidity'] < 50",
            "#metrics['humidity'] <= 50",
            "#metrics['humidity'] == 50",
            "#metrics['humidity'] != 50",
            "50 > #metrics['humidity']",
            "50 >= #metrics['humidity']",
            "50 < #metrics['humidity']",
            "50 <= #metrics['humidity']",
            "50 == #metrics['humidity']",
            "50 != #metrics['humidity']"
    })
    @DisplayName("누락된 metric은 모든 비교 연산에서 false를 반환한다")
    void missingMetricNeverMatches(String expression) {
        FlowExecutionContext context = context(Map.of("temperature", 31.2));

        assertFalse(evaluator.evaluate(expression, context));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "#metrics['temperature'] > 30",
            "#metrics['temperature'] >= 30",
            "#metrics['temperature'] < 30",
            "#metrics['temperature'] <= 30",
            "#metrics['temperature'] == 30",
            "#metrics['temperature'] != 30",
            "30 > #metrics['temperature']",
            "30 >= #metrics['temperature']",
            "30 < #metrics['temperature']",
            "30 <= #metrics['temperature']",
            "30 == #metrics['temperature']",
            "30 != #metrics['temperature']"
    })
    @DisplayName("숫자가 아닌 metric은 모든 비교 연산에서 false를 반환한다")
    void nonNumericMetricNeverMatches(String expression) {
        FlowExecutionContext context = context(Map.of("temperature", "not-a-number"));

        assertFalse(evaluator.evaluate(expression, context));
    }

    @Test
    @DisplayName("event를 통해 접근한 누락 metric도 false를 반환한다")
    void missingEventMetricNeverMatches() {
        FlowExecutionContext context = context(Map.of("temperature", 31.2));

        assertFalse(evaluator.evaluate("#event.metrics['humidity'] != 50", context));
    }

    @Test
    @DisplayName("null metric은 false를 반환한다")
    void nullMetricNeverMatches() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("temperature", null);
        FlowExecutionContext context = mock(FlowExecutionContext.class);
        when(context.metrics()).thenReturn(metrics);

        assertFalse(evaluator.evaluate("#metrics['temperature'] != 50", context));
    }

    @Test
    @DisplayName("유한하지 않은 숫자 metric은 false를 반환한다")
    void nonFiniteMetricNeverMatches() {
        assertFalse(evaluator.evaluate(
                "#metrics['temperature'] != 50",
                context(Map.of("temperature", Double.NaN))
        ));
        assertFalse(evaluator.evaluate(
                "#metrics['temperature'] > 50",
                context(Map.of("temperature", Double.POSITIVE_INFINITY))
        ));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidProjectionMetrics")
    @DisplayName("Map projection으로 접근한 유효하지 않은 metric도 false를 반환한다")
    void invalidMetricFromEntrySetNeverMatches(String caseName, Object invalidMetric) {
        FlowExecutionContext context = context(Map.of("temperature", invalidMetric));

        assertFalse(evaluator.evaluate("#metrics.![value][0] != 50", context));
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

        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(null, context));
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
        assertDoesNotThrow(() -> evaluator.validateExpression("#metrics['temperature'] > 30"));
    }

    @Test
    @DisplayName("문법이 잘못된 expression은 검증 중 거부한다")
    void rejectInvalidExpressionSyntax() {
        assertThrows(RuntimeException.class, () -> evaluator.validateExpression("#metrics['temperature'] >"));
    }

    @Test
    @DisplayName("검증할 expression은 필수이며 허용 길이를 초과할 수 없습니다")
    void rejectMissingOrOversizedExpression() {
        String oversizedExpression = "1".repeat(1001);

        assertThrows(IllegalArgumentException.class, () -> evaluator.validateExpression(null));
        assertThrows(IllegalArgumentException.class, () -> evaluator.validateExpression(" "));
        assertThrows(IllegalArgumentException.class, () -> evaluator.validateExpression(oversizedExpression));
    }

    @Test
    @DisplayName("expression 캐시는 상한에 도달하면 정리한 뒤 계속 사용할 수 있습니다")
    void clearsExpressionCacheAtCapacity() {
        for (int index = 0; index <= 1024; index++) {
            int expressionNumber = index;
            assertDoesNotThrow(() -> evaluator.validateExpression(expressionNumber + " >= 0"));
        }

        assertDoesNotThrow(() -> evaluator.validateExpression("1024 >= 0"));
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

    private static Stream<Arguments> invalidProjectionMetrics() {
        return Stream.of(
                Arguments.of("비숫자", "not-a-number"),
                Arguments.of("NaN", Double.NaN),
                Arguments.of("양의 infinity", Double.POSITIVE_INFINITY),
                Arguments.of("음의 infinity", Float.NEGATIVE_INFINITY)
        );
    }
}
