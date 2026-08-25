package com.nhnacademy.insightonruleengine.flow.domain.node.params;

import static com.nhnacademy.insightonruleengine.flow.domain.node.params.action.Severity.WARNING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.AlertParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuntimeStateParamsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("ALERT Runtime state 설정을 생략하면 3회, 5분, 30분을 사용합니다.")
    void alertDefaultConfigTest() throws Exception {
        AlertParams alertParams = objectMapper.readValue(
                "{\"title\":\"온도 경고\",\"severity\":\"WARNING\",\"message\":\"고온\"}",
                AlertParams.class
        );
        assertEquals(3, alertParams.requiredCount());
        assertEquals(300, alertParams.countTimeoutSeconds());
        assertEquals(1800, alertParams.cooldownSeconds());
    }

    @Test
    @DisplayName("requiredCount가 2 이상이면 누락한 판단 시간은 5분이고 명시한 0초는 거부합니다.")
    void requiredCountCountTimeoutSecondsTest() {
        AlertParams alertParams = new AlertParams("온도 경고", WARNING, "고온", 2, null, 0);

        assertEquals(300, alertParams.countTimeoutSeconds());
        assertThrows(
                IllegalArgumentException.class,
                () -> new AlertParams("온도 경고", WARNING, "고온", 2, 0, 0)
        );
    }
}
