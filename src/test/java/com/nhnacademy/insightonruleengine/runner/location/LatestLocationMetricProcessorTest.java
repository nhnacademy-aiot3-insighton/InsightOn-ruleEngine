package com.nhnacademy.insightonruleengine.runner.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

class LatestLocationMetricProcessorTest {

    @Test
    @DisplayName("다른 센서에서 수신한 최신 metric snapshot을 Location Flow 입력으로 사용한다")
    void usesLatestLocationMetrics() {
        LocationMetricStateRepository repository = event -> Map.of(
                "temperature", event.metrics().get("temperature"),
                "humidity", 60
        );
        LatestLocationMetricProcessor processor = new LatestLocationMetricProcessor(repository);
        FlowExecutionContext context = context(flow(1L, 10L), event(1L, 10L, 100L,
                Map.of("temperature", 24.1)));

        processor.prepare(context);

        assertEquals(24.1, context.metric("temperature"));
        assertEquals(60, context.metric("humidity"));
        assertEquals(context.metrics(), context.event().metrics());
    }

    @Test
    @DisplayName("Flow와 event의 group/location 범위가 다르면 거부한다")
    void rejectDifferentScope() {
        LatestLocationMetricProcessor processor = new LatestLocationMetricProcessor(SensorEvent::metrics);
        FlowExecutionContext context = context(flow(2L, 10L), event(1L, 10L, 100L,
                Map.of("temperature", 24.1)));

        assertThrows(IllegalArgumentException.class, () -> processor.prepare(context));
    }

    private FlowExecutionContext context(FlowDefinition flow, SensorEvent event) {
        return new FlowExecutionContext(flow, event);
    }

    private FlowDefinition flow(Long groupId, Long locationId) {
        return new FlowDefinition(
                1L, groupId, locationId, "location flow", null, FlowStatus.ACTIVE,
                OffsetDateTime.now(), List.of(), List.of());
    }

    private SensorEvent event(Long groupId, Long locationId, Long sensorId, Map<String, Object> metrics) {
        return new SensorEvent(
                groupId, locationId, sensorId, metrics, Instant.parse("2026-08-13T00:00:00Z"));
    }
}
