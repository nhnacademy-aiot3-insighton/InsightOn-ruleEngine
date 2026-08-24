package com.nhnacademy.insightonruleengine.runner.orchestrator;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nhnacademy.insightonruleengine.runner.FlowRunner;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.dto.TelemetryEventMessage;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelemetryExecutionOrchestratorTest {

    private static final long GROUP_ID = 1L;
    private static final long LOCATION_ID = 10L;

    @Mock
    private FlowRunner flowRunner;

    private TelemetryExecutionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        StaleTelemetryDetector staleTelemetryDetector = new StaleTelemetryDetector();
        orchestrator = new TelemetryExecutionOrchestrator(staleTelemetryDetector, flowRunner);
    }

    @Test
    @DisplayName("유효한 Telemetry는 공통 FlowRunner 실행 경로로 전달합니다")
    void delegateValidTelemetryTest() {
        orchestrator.orchestrate(message("2026-08-20T01:00:00Z"));

        verify(flowRunner).run(new SensorEvent(
                GROUP_ID,
                LOCATION_ID,
                101L,
                Map.of("temperature", 31.5),
                OffsetDateTime.parse("2026-08-20T01:00:00Z").toInstant()
        ));
    }

    @Test
    @DisplayName("같은 Sensor의 역순 Telemetry는 FlowRunner에 전달하지 않고 폐기합니다")
    void discardStaleTelemetryTest() {
        orchestrator.orchestrate(message("2026-08-20T02:00:00Z"));

        orchestrator.orchestrate(message("2026-08-20T01:00:00Z"));

        verify(flowRunner, times(1)).run(new SensorEvent(
                GROUP_ID,
                LOCATION_ID,
                101L,
                Map.of("temperature", 31.5),
                OffsetDateTime.parse("2026-08-20T02:00:00Z").toInstant()
        ));
    }

    private TelemetryEventMessage message(String time) {
        return new TelemetryEventMessage(
                GROUP_ID,
                LOCATION_ID,
                "101",
                Map.of("temperature", 31.5),
                OffsetDateTime.parse(time)
        );
    }

}
