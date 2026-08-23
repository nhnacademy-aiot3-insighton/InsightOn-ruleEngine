package com.nhnacademy.insightonruleengine.runner.execution.location;

import com.nhnacademy.insightonruleengine.runner.dto.FlowExecutionContext;

/**
 * Location 기반 메트릭을 처리하는 프로세서 인터페이스.
 * Flow 실행 전 특정 Location의 메트릭 데이터를 준비하고 처리하는 역할을 담당
 */
public interface LocationMetricProcessor {

    /**
     * Flow 실행에 필요한 메트릭 데이터를 준비한다.
     *
     * @param context Flow 실행 컨텍스트
     */
    void prepare(FlowExecutionContext context);
}
