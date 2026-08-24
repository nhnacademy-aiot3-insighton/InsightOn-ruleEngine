package com.nhnacademy.insightonruleengine.runner.recovery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlowRuntimeRecoveryInitializerTest {

    @Mock
    private FlowRuntimeRecoveryService flowRuntimeRecoveryService;

    @InjectMocks
    private FlowRuntimeRecoveryInitializer initializer;

    @Test
    @DisplayName("애플리케이션 시작 시 모든 Flow Runtime 데이터를 재구축합니다")
    void rebuildRuntimeTest() {
        when(flowRuntimeRecoveryService.rebuildAll()).thenReturn(3);

        initializer.rebuildRuntime();

        verify(flowRuntimeRecoveryService).rebuildAll();
    }

    @Test
    @DisplayName("Redis 재구축 실패가 애플리케이션 시작을 막지 않습니다")
    void tolerateRebuildFailureTest() {
        when(flowRuntimeRecoveryService.rebuildAll())
                .thenThrow(new IllegalStateException("Redis unavailable"));

        assertDoesNotThrow(initializer::rebuildRuntime);
    }
}
