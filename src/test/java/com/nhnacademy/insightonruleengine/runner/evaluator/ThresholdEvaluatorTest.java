package com.nhnacademy.insightonruleengine.runner.evaluator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.runner.dto.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ThresholdEvaluatorTest {

    private final ThresholdEvaluator evaluator = new ThresholdEvaluator();

    @Test
    @DisplayName("ThresholdEvaluator는 FlowExecutionContext가 보유한 현재 패킷 metrics를 평가한다")
    void evaluateCurrentPacketMetrics() {
        FlowExecutionContext context = new FlowExecutionContext(
                new FlowDefinition(
                        1L,
                        1L,
                        10L,
                        "location flow",
                        null,
                        FlowStatus.ACTIVE,
                        OffsetDateTime.now(),
                        List.of(),
                        List.of()
                ),
                new SensorEvent(1L, 10L, 100L, Map.of("temperature", 30), Instant.now())
        );

        assertTrue(evaluator.evaluate("#metrics['temperature'] > 25", context));
    }
}
