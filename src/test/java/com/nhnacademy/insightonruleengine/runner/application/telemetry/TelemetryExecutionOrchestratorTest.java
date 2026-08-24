package com.nhnacademy.insightonruleengine.runner.orchestrator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.runner.FlowRunner;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.dto.TelemetryEventMessage;
import com.nhnacademy.insightonruleengine.runner.recovery.FlowRuntimeRecoveryService;
import java.time.OffsetDateTime;
import java.util.List;
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
    private FlowRuntimeRecoveryService flowRuntimeRecoveryService;

    @Mock
    private FlowRunner flowRunner;

    private TelemetryExecutionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        StaleTelemetryDetector staleTelemetryDetector = new StaleTelemetryDetector();
        orchestrator = new TelemetryExecutionOrchestrator(
                staleTelemetryDetector,
                flowRuntimeRecoveryService,
                flowRunner
        );
    }

    @Test
    @DisplayName("장소 Route의 모든 ACTIVE Flow를 순서대로 실행합니다")
    void executeAllActiveFlowsTest() {
        FlowDefinition first = flow(100L);
        FlowDefinition second = flow(200L);
        when(flowRuntimeRecoveryService.findActiveFlows(GROUP_ID, LOCATION_ID))
                .thenReturn(List.of(first, second));

        orchestrator.orchestrate(message("2026-08-20T01:00:00Z"));

        verify(flowRunner).runFlow(eq(first), any(SensorEvent.class));
        verify(flowRunner).runFlow(eq(second), any(SensorEvent.class));
    }

    @Test
    @DisplayName("한 Flow 실행이 실패해도 다음 Flow 실행을 계속합니다")
    void isolateFlowFailureTest() {
        FlowDefinition failedFlow = flow(100L);
        FlowDefinition nextFlow = flow(200L);
        when(flowRuntimeRecoveryService.findActiveFlows(GROUP_ID, LOCATION_ID))
                .thenReturn(List.of(failedFlow, nextFlow));
        doThrow(new IllegalStateException("실행 실패"))
                .when(flowRunner).runFlow(
                        eq(failedFlow),
                        any(SensorEvent.class)
                );

        orchestrator.orchestrate(message("2026-08-20T01:00:00Z"));

        verify(flowRunner).runFlow(eq(nextFlow), any(SensorEvent.class));
    }

    @Test
    @DisplayName("같은 Sensor의 역순 Telemetry는 Route 조회와 Flow 실행 없이 폐기합니다")
    void discardStaleTelemetryTest() {
        when(flowRuntimeRecoveryService.findActiveFlows(GROUP_ID, LOCATION_ID)).thenReturn(List.of());
        orchestrator.orchestrate(message("2026-08-20T02:00:00Z"));

        orchestrator.orchestrate(message("2026-08-20T01:00:00Z"));

        verify(flowRuntimeRecoveryService).findActiveFlows(GROUP_ID, LOCATION_ID);
        verify(flowRunner, never()).runFlow(
                any(FlowDefinition.class),
                any(SensorEvent.class)
        );
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

    private FlowDefinition flow(Long flowId) {
        return new FlowDefinition(
                flowId,
                GROUP_ID,
                LOCATION_ID,
                "Flow " + flowId,
                null,
                FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-20T00:00:00Z"),
                List.of(),
                List.of()
        );
    }
}
