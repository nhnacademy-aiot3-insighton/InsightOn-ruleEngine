package com.nhnacademy.insightonruleengine.runner.application.telemetry;

import com.nhnacademy.insightonruleengine.runner.application.FlowRunner;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 수신된 Telemetry의 시간 순서를 확인한 뒤 Flow 실행을 위임합니다.
@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryExecutionOrchestrator {

    private final StaleTelemetryDetector staleTelemetryDetector;
    private final FlowRunner flowRunner;

    public void orchestrate(SensorEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event는 필수입니다.");
        }

        if (staleTelemetryDetector.isStale(
                event.groupId(),
                event.locationId(),
                event.sensorId(),
                event.timestamp()
        )) {
            log.debug("순서가 뒤바뀌거나 오래된 Telemetry 메시지를 폐기합니다. "
                            + "groupId={}, locationId={}, sensorId={}, timestamp={}",
                    event.groupId(), event.locationId(), event.sensorId(), event.timestamp());
            return;
        }

        flowRunner.run(event);
    }
}
