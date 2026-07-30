package com.nhnacademy.insightonruleengine.node.domain.params.trigger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScheduleParamsTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("ScheduleParams cron은 Spring cron 문법을 검증한다")
    void validateCronExpression() {
        assertTrue(validator.validate(new ScheduleParams("0 */5 * * * *")).isEmpty());
        assertFalse(validator.validate(new ScheduleParams("invalid")).isEmpty());
    }

    @Test
    @DisplayName("ScheduleParams cron 빈 값은 NotBlank로 거부한다")
    void rejectBlankCron() {
        assertFalse(validator.validate(new ScheduleParams(" ")).isEmpty());
    }
}
