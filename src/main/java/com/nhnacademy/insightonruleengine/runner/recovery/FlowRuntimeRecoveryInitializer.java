package com.nhnacademy.insightonruleengine.runner.recovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 애플리케이션 시작 뒤 PostgreSQL의 ACTIVE Flow로 Redis Runtime 데이터를 재구축합니다.
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowRuntimeRecoveryInitializer {

    private final FlowRuntimeRecoveryService flowRuntimeRecoveryService;

    // Redis 장애가 애플리케이션 기동 자체를 막지 않도록 실패를 기록하고 종료합니다.
    @EventListener(ApplicationReadyEvent.class)
    public void rebuildRuntime() {
        try {
            int rebuiltFlowCount = flowRuntimeRecoveryService.rebuildAll();
            log.info("Flow Runtime startup rebuild completed. activeFlowCount={}", rebuiltFlowCount);
        } catch (RuntimeException exception) {
            log.error("Flow Runtime startup rebuild failed.", exception);
        }
    }
}
