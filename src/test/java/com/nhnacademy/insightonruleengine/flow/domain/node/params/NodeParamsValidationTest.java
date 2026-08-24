package com.nhnacademy.insightonruleengine.flow.domain.node.params;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.ExternalNotificationParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.TimeWindowParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.TimerParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.trigger.SensorParams;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NodeParamsValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("TimeWindowParams는 정상 startTime/endTime을 생성한다")
    void createTimeWindowParams() {
        TimeWindowParams params = new TimeWindowParams(LocalTime.of(9, 0), LocalTime.of(18, 0));

        assertTrue(validator.validate(params).isEmpty());
    }

    @Test
    @DisplayName("TimeWindowParams startTime이 endTime보다 늦으면 예외가 발생한다")
    void rejectTimeWindowStartAfterEnd() {
        LocalTime start = LocalTime.of(18, 0);
        LocalTime end = LocalTime.of(9, 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> new TimeWindowParams(start, end)
        );
    }

    @Test
    @DisplayName("TimeWindowParams startTime과 endTime이 같으면 예외가 발생한다")
    void rejectTimeWindowSameStartAndEnd() {
        LocalTime time = LocalTime.of(9, 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> new TimeWindowParams(time, time)
        );
    }

    @Test
    @DisplayName("TimeWindowParams startTime/endTime은 필수 값이다")
    void rejectTimeWindowNullTime() {
        assertFalse(validator.validate(new TimeWindowParams(null, LocalTime.of(18, 0))).isEmpty());
        assertFalse(validator.validate(new TimeWindowParams(LocalTime.of(9, 0), null)).isEmpty());
    }

    @Test
    @DisplayName("TimerParams intervalSeconds는 양수여야 한다")
    void validateTimerIntervalSeconds() {
        assertTrue(validator.validate(new TimerParams(1)).isEmpty());
        assertFalse(validator.validate(new TimerParams(0)).isEmpty());
        assertFalse(validator.validate(new TimerParams(-1)).isEmpty());
    }

    @Test
    @DisplayName("SensorParams sensorId는 양수여야 한다.")
    void validateSensorId() {
        assertTrue(validator.validate(new SensorParams(1L)).isEmpty());
        assertFalse(validator.validate(new SensorParams(null)).isEmpty());
        assertFalse(validator.validate(new SensorParams(0L)).isEmpty());
        assertFalse(validator.validate(new SensorParams(-1L)).isEmpty());
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
