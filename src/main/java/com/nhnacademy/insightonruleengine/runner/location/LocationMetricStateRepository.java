package com.nhnacademy.insightonruleengine.runner.location;

import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import java.util.Map;

/** 다중 엔진 인스턴스가 공유하는 location별 최신 metric 상태 저장소입니다. */
public interface LocationMetricStateRepository {

    Map<String, Object> mergeAndGet(SensorEvent event);
}
