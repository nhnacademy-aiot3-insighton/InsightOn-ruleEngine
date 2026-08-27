package com.nhnacademy.insightonruleengine.heartbeat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HeartbeatSchedulerTest {

    @Mock
    private EngineHeartbeatService heartbeatService;

    // 예약 실행이 별도 판단 없이 heartbeat 갱신 책임으로 위임되는지 확인해줍니다.
    @Test
    @DisplayName("스케줄 실행 시 현재 엔진 heartbeat를 한 번 갱신한다")
    void scheduledRefreshTest() {
        HeartbeatScheduler scheduler = new HeartbeatScheduler(heartbeatService);

        scheduler.refreshHeartbeat();

        verify(heartbeatService).refreshHeartbeat();
    }

    @Test
    @DisplayName("heartbeat 갱신 실패를 전파하지 않고 다음 실행에서 복구를 확인한다")
    void refreshFailureAndRecoveryTest() {
        HeartbeatScheduler scheduler = new HeartbeatScheduler(heartbeatService);
        doThrow(new IllegalStateException("Redis unavailable"))
                .doNothing()
                .when(heartbeatService)
                .refreshHeartbeat();

        assertDoesNotThrow(scheduler::refreshHeartbeat);
        assertDoesNotThrow(scheduler::refreshHeartbeat);

        verify(heartbeatService, times(2)).refreshHeartbeat();
    }
}
