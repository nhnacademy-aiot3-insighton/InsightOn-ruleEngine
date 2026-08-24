package com.nhnacademy.insightonruleengine.runner.orchestrator;

import com.nhnacademy.insightonruleengine.runner.FlowRunner;
import com.nhnacademy.insightonruleengine.runner.dto.TelemetryEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

//수신된 Telemetry 메시지의 유효성을 검사한 뒤 공통 Flow 실행 경로로 전달합니다.
@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryExecutionOrchestrator {

    private final StaleTelemetryDetector staleTelemetryDetector;
    private final FlowRunner flowRunner;

    //Telemetry 메시지를 검증하고 Trigger 선별과 Flow 실행을 FlowRunner에 위임합니다.
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

        flowRunner.run(message.toSensorEvent());
    }
}
