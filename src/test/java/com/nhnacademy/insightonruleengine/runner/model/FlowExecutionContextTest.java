package com.nhnacademy.insightonruleengine.runner.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlowExecutionContextTest {

    @Test
    void exposesEventMetricsAndTimestamp() {
        Instant timestamp = Instant.parse("2026-08-24T00:00:00Z");
        FlowExecutionContext context = new FlowExecutionContext(
                flow(),
                new SensorEvent(1L, 10L, 100L, Map.of("temperature", 31.5), timestamp)
        );

        assertEquals(Map.of("temperature", 31.5), context.metrics());
        assertEquals(31.5, context.metric("temperature"));
        assertEquals(timestamp, context.timestamp());
    }

    @Test
    void rejectsMissingFlowOrEvent() {
        SensorEvent event = new SensorEvent(
                1L,
                10L,
                100L,
                Map.of("temperature", 31.5),
                Instant.parse("2026-08-24T00:00:00Z")
        );
        FlowDefinition flow = flow();

        assertThrows(IllegalArgumentException.class, () -> new FlowExecutionContext(null, event));
        assertThrows(IllegalArgumentException.class, () -> new FlowExecutionContext(flow, null));
    }

    @Test
    void rejectsMissingEventMetrics() {
        SensorEvent nullMetricsEvent = mock(SensorEvent.class);
        when(nullMetricsEvent.metrics()).thenReturn(null);
        SensorEvent emptyMetricsEvent = mock(SensorEvent.class);
        when(emptyMetricsEvent.metrics()).thenReturn(Map.of());
        FlowDefinition flow = flow();

        assertThrows(IllegalArgumentException.class,
                () -> new FlowExecutionContext(flow, nullMetricsEvent));
        assertThrows(IllegalArgumentException.class,
                () -> new FlowExecutionContext(flow, emptyMetricsEvent));
    }

    @Test
    void rejectsMissingMetricName() {
        FlowExecutionContext context = new FlowExecutionContext(
                flow(),
                new SensorEvent(
                        1L,
                        10L,
                        100L,
                        Map.of("temperature", 31.5),
                        Instant.parse("2026-08-24T00:00:00Z"))
        );

        assertThrows(IllegalArgumentException.class, () -> context.metric(null));
        assertThrows(IllegalArgumentException.class, () -> context.metric(" "));
    }

    private FlowDefinition flow() {
        return new FlowDefinition(
                100L,
                1L,
                10L,
                "테스트 Flow",
                null,
                FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-24T00:00:00Z"),
                List.of(),
                List.of()
        );
    }
}
