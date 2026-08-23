package com.nhnacademy.insightonruleengine.runner.execution.location;

import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 현재 패킷의 Location 입력만 검증한다.
 * 이전 패킷의 metric state나 집계 결과를 저장하지 않는다.
 */
@Component
public class CurrentPacketLocationMetricProcessor implements LocationMetricProcessor {

    @Override
    public void prepare(FlowExecutionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context는 필수입니다.");
        }
        if (!Objects.equals(context.flow().locationId(), context.event().locationId())) {
            throw new IllegalArgumentException("Flow locationId와 event locationId가 일치하지 않습니다.");
        }
        context.event().metrics().keySet().forEach(metricKey -> {
            if (metricKey == null || metricKey.isBlank()) {
                throw new IllegalArgumentException("metric key는 필수입니다.");
            }
        });
    }
}
