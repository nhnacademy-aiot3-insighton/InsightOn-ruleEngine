package com.nhnacademy.insightonruleengine.flow.domain.node.params.trigger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScheduleParamsTest {

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
