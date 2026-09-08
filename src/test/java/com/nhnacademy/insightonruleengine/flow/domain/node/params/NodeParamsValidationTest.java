package com.nhnacademy.insightonruleengine.flow.domain.node.params;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.EventGateParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.TimeWindowParams;
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
    @DisplayName("TimeWindowParams는 자정을 지나는 시간 범위를 허용한다")
    void createOvernightTimeWindowParams() {
        TimeWindowParams params = new TimeWindowParams(LocalTime.of(22, 0), LocalTime.of(6, 0));

        assertTrue(validator.validate(params).isEmpty());
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
    @DisplayName("EventGateParams는 반복 확인과 최소 실행 간격을 각각 또는 함께 설정할 수 있다")
    void validateEventGateParams() {
        assertTrue(validator.validate(new EventGateParams(1, null, 60)).isEmpty());
        assertTrue(validator.validate(new EventGateParams(3, 300, 0)).isEmpty());
        assertTrue(validator.validate(new EventGateParams(3, 300, 60)).isEmpty());
    }

    @Test
    @DisplayName("EventGateParams는 무효하거나 아무 동작도 하지 않는 설정을 거부한다")
    void rejectInvalidEventGateParams() {
        assertFalse(validator.validate(new EventGateParams(0, null, 60)).isEmpty());
        assertFalse(validator.validate(new EventGateParams(1, null, -1)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new EventGateParams(2, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new EventGateParams(1, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new EventGateParams(null, null, 60));
        assertThrows(IllegalArgumentException.class, () -> new EventGateParams(1, null, null));
    }

    @Test
    @DisplayName("SensorParams sensorId는 양수여야 한다.")
    void validateSensorId() {
        assertTrue(validator.validate(new SensorParams(1L)).isEmpty());
        assertFalse(validator.validate(new SensorParams(null)).isEmpty());
        assertFalse(validator.validate(new SensorParams(0L)).isEmpty());
        assertFalse(validator.validate(new SensorParams(-1L)).isEmpty());
    }
}
