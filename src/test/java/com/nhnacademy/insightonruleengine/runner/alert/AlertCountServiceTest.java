package com.nhnacademy.insightonruleengine.runner.alert;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.AlertParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AlertCountServiceTest {

    @Mock
    private AlertCountRedisRepository alertCountRedisRepository;

    private AlertCountService alertCountService;

    @BeforeEach
    public void setUp() {
        alertCountService = new AlertCountService(alertCountRedisRepository);
    }

    @Test
    @DisplayName("Count, Cooldown을 사용하지 않을 때 ALERT 허용합니다.")
    void defaultAlertTest() {
        AlertParams params = alertParams(1, null, 0);
        assertTrue(alertCountService.shouldPublish(1L, 10L, params));
        verifyNoInteractions(alertCountRedisRepository);
    }

    @Test
    @DisplayName("ALERT에 도달 시 Redis의 Count와 Cooldown 변경됩니다.")
    void alertTransactionTest() {
        AlertParams params = alertParams(3, 10, 30);
        when(alertCountRedisRepository.incrementAndCheck(1L, 10L, 3, 10, 30))
                .thenReturn(false, true);
        assertFalse(alertCountService.shouldPublish(1L, 10L, params));
        assertTrue(alertCountService.shouldPublish(1L, 10L, params));

        verify(alertCountRedisRepository, times(2))
                .incrementAndCheck(1L, 10L, 3, 10, 30);

    }

    @Test
    @DisplayName("AlertParams가 없으면 실행되지 않습니다.")
    void missingAlertTest() {
        assertThrows(
                IllegalArgumentException.class,
                () -> alertCountService.shouldPublish(1L, 10L, null)
        );
    }

    @Test
    @DisplayName("requiredCount가 1이어도 cooldown 사용시 Redis에 저장합니다.")
    void cooldownAlertTest() {
        AlertParams params = alertParams(1, null, 30);
        when(alertCountRedisRepository.incrementAndCheck(1L, 10L, 1, 0, 30))
                .thenReturn(true);
        assertTrue(alertCountService.shouldPublish(1L, 10L, params));
        verify(alertCountRedisRepository).incrementAndCheck(1L, 10L, 1, 0, 30);
    }

    private AlertParams alertParams(
            int requiredCount,
            Integer countTimeoutSeconds,
            Integer cooldownSeconds
    ) {
        return new AlertParams(
                "온도 경고",
                "WARNING",
                "설정 온도를 초과했습니다.",
                requiredCount,
                countTimeoutSeconds,
                cooldownSeconds
        );
    }
}
