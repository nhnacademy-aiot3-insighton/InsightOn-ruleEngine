package com.nhnacademy.insightonruleengine.node.domain.params;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhnacademy.insightonruleengine.node.domain.params.action.ExternalNotificationParams;
import com.nhnacademy.insightonruleengine.node.domain.params.filter.TimeWindowParams;
import com.nhnacademy.insightonruleengine.node.domain.params.filter.TimerParams;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NodeParamsValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("TimeWindowParams는 정상 startTime/endTime을 생성한다")
    void createTimeWindowParams() {
        TimeWindowParams params = new TimeWindowParams(LocalTime.of(9, 0), LocalTime.of(18, 0));

        assertTrue(validator.validate(params).isEmpty());
    }

    @Test
    @DisplayName("TimeWindowParams startTime이 endTime보다 늦으면 예외가 발생한다")
    void rejectTimeWindowStartAfterEnd() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TimeWindowParams(LocalTime.of(18, 0), LocalTime.of(9, 0))
        );
    }

    @Test
    @DisplayName("TimeWindowParams startTime과 endTime이 같으면 예외가 발생한다")
    void rejectTimeWindowSameStartAndEnd() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TimeWindowParams(LocalTime.of(9, 0), LocalTime.of(9, 0))
        );
    }

    @Test
    @DisplayName("TimerParams intervalSeconds는 양수여야 한다")
    void validateTimerIntervalSeconds() {
        assertTrue(validator.validate(new TimerParams(1)).isEmpty());
        assertFalse(validator.validate(new TimerParams(0)).isEmpty());
        assertFalse(validator.validate(new TimerParams(-1)).isEmpty());
    }

    @Test
    @DisplayName("ExternalNotificationParams channel은 TELEGRAM 또는 EMAIL만 허용한다")
    void validateExternalNotificationChannel() {
        assertTrue(validator.validate(new ExternalNotificationParams("TELEGRAM")).isEmpty());
        assertTrue(validator.validate(new ExternalNotificationParams("EMAIL")).isEmpty());
        assertFalse(validator.validate(new ExternalNotificationParams("SLACK")).isEmpty());
        assertFalse(validator.validate(new ExternalNotificationParams(" ")).isEmpty());
    }
}
