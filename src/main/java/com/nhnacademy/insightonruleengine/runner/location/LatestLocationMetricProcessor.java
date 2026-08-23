package com.nhnacademy.insightonruleengine.runner.location;

import com.nhnacademy.insightonruleengine.runner.dto.FlowExecutionContext;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** location의 모든 센서에서 수신한 metric별 최신 값을 실행 컨텍스트에 준비합니다. */
@Component
@RequiredArgsConstructor
public class LatestLocationMetricProcessor implements LocationMetricProcessor {

    private final LocationMetricStateRepository stateRepository;

    @Override
    public void prepare(FlowExecutionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context는 필수입니다.");
        }
        if (!Objects.equals(context.flow().groupId(), context.event().groupId())
                || !Objects.equals(context.flow().locationId(), context.event().locationId())) {
            throw new IllegalArgumentException("Flow와 event의 groupId/locationId가 일치하지 않습니다.");
        }
        context.replaceMetrics(stateRepository.mergeAndGet(context.event()));
    }
}
