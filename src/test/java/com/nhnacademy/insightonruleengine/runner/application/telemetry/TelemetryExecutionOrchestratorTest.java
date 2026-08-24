package com.nhnacademy.insightonruleengine.runner.application.telemetry;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nhnacademy.insightonruleengine.runner.application.FlowRunner;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelemetryExecutionOrchestratorTest {

    @Mock
    private FlowRunner flowRunner;

    private TelemetryExecutionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new TelemetryExecutionOrchestrator(new StaleTelemetryDetector(), flowRunner);
    }

    @Test
    @DisplayName("최신 Telemetry를 FlowRunner에 전달합니다")
    void executeLatestTelemetryTest() {
        SensorEvent event = event("2026-08-20T01:00:00Z");

        orchestrator.orchestrate(event);

        verify(flowRunner).run(event);
    }

    @Test
    @DisplayName("같은 Sensor의 역순 Telemetry는 FlowRunner에 전달하지 않습니다")
    void discardStaleTelemetryTest() {
        SensorEvent latest = event("2026-08-20T02:00:00Z");
        SensorEvent stale = event("2026-08-20T01:00:00Z");

        orchestrator.orchestrate(latest);
        orchestrator.orchestrate(stale);

        verify(flowRunner).run(latest);
        verify(flowRunner, never()).run(stale);
    }

    @Test
    @DisplayName("Telemetry 이벤트는 필수입니다")
    void nullEventTest() {
        assertThrows(IllegalArgumentException.class, () -> orchestrator.orchestrate(null));
    }

    private SensorEvent event(String timestamp) {
        return new SensorEvent(
                1L,
                10L,
                101L,
                Map.of("temperature", 31.5),
                Instant.parse(timestamp)
        );
    }
}
