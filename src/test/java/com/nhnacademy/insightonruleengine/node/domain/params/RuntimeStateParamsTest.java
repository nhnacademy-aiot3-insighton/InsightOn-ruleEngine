package com.nhnacademy.insightonruleengine.node.domain.params;

import static com.nhnacademy.insightonruleengine.flow.domain.node.params.action.Severity.WARNING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.AlertParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class RuntimeStateParamsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("ALERT Runtime state 설정을 생략하면 1,0을 사용합니다.")
    void alertDefaultConfigTest() throws Exception {
        AlertParams alertParams = objectMapper.readValue(
                "{\"title\":\"온도 경고\",\"severity\":\"WARNING\",\"message\":\"고온\"}",
                AlertParams.class
        );
        assertEquals(1, alertParams.requiredCount());
        assertNull(alertParams.countTimeoutSeconds());
        assertEquals(0, alertParams.cooldownSeconds());
    }

    @Test
    @DisplayName("requiredCount가 2 이상일때 countTimeoutSeconds는 양수여야 합니다.")
    void requiredCountCountTimeoutSecondsTest() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AlertParams("온도 경고", WARNING, "고온", 2, null, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AlertParams("온도 경고", WARNING, "고온", 2, 0, 0)
        );
    }
}
