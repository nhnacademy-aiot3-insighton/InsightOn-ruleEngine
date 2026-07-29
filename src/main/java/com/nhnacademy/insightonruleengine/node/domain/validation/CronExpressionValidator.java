package com.nhnacademy.insightonruleengine.node.domain.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.scheduling.support.CronExpression;

public class CronExpressionValidator implements ConstraintValidator<ValidCron, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }

        try {
            CronExpression.parse(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
