package com.nhnacademy.insightonruleengine.runner.orchestrator;

import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.runner.FlowRunner;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.dto.TelemetryEventMessage;
import com.nhnacademy.insightonruleengine.runner.recovery.FlowRuntimeRecoveryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

//수신된 Telemetry 메시지의 유효성 검사, ACTIVE Flow 목록 조회 및 Flow별 격리 실행을 총괄하는 오케스트레이터입니다.
@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryExecutionOrchestrator {

    private final StaleTelemetryDetector staleTelemetryDetector;
    private final FlowRuntimeRecoveryService flowRuntimeRecoveryService;
    private final FlowRunner flowRunner;

    //Telemetry 메시지를 검증하고 대상 장소의 모든 ACTIVE Flow를 독립적으로 실행합니다.
    public void orchestrate(TelemetryEventMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message는 필수입니다.");
        }
        //역순/과거 시각 Telemetry 패킷 검사 및 폐기
        if (staleTelemetryDetector.isStale(message.groupId(), message.locationId(), message.sensorId(),
                message.time())) {
            log.warn("Discarding out-of-order/stale Telemetry message. groupId={}, locationId={}, sensorId={}, time={}",
                    message.groupId(), message.locationId(), message.sensorId(), message.time());
            return;
        }

        // 2. locationId 기준 ACTIVE Flow 목록 조회 (Route MISS 시 PostgreSQL 자동 복구)
        List<FlowDefinition> activeFlows = flowRuntimeRecoveryService.findActiveFlows(
                message.groupId(),
                message.locationId()
        );

        if (activeFlows.isEmpty()) {
            log.debug("No active flows found for groupId={}, locationId={}",
                    message.groupId(), message.locationId());
            return;
        }

        SensorEvent sensorEvent = message.toSensorEvent();

        // 3. Flow별 독립 실행 및 실패 격리
        for (FlowDefinition flow : activeFlows) {
            try {
                flowRunner.runFlow(flow, sensorEvent);
            } catch (Exception exception) {
                log.error("Flow execution failed. flowId={}, groupId={}, locationId={}, sensorId={}",
                        flow.flowId(), flow.groupId(), flow.locationId(), message.sensorId(), exception);
            }
        }
    }
}
