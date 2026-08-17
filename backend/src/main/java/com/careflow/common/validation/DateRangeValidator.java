package com.careflow.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

import java.time.LocalDate;

public class DateRangeValidator implements ConstraintValidator<ValidDateRange, Object> {

    private String startField;
    private String endField;
    private String message;

    @Override
    public void initialize(ValidDateRange constraint) {
        this.startField = constraint.start();
        this.endField = constraint.end();
        this.message = constraint.message();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        BeanWrapperImpl wrapper = new BeanWrapperImpl(value);
        LocalDate start = (LocalDate) wrapper.getPropertyValue(startField);
        LocalDate end = (LocalDate) wrapper.getPropertyValue(endField);

        if (start == null || end == null || !end.isBefore(start)) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(endField)
                .addConstraintViolation();
        return false;
    }
}
