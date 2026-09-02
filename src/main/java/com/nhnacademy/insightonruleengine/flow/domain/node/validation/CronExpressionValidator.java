package com.nhnacademy.insightonruleengine.flow.domain.node.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.scheduling.support.CronExpression;

public class CronExpressionValidator implements ConstraintValidator<ValidCron, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }

        String normalized = value.trim();
        String[] fields = normalized.split("\\s+");
        if (fields.length != 6 || !"0".equals(fields[0])) {
            return false;
        }

        try {
            CronExpression.parse(normalized);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
