package com.careflow.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Asserts that an optional end date is not earlier than its start date.
 * Applied at type level to records exposing both accessors.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateRangeValidator.class)
public @interface ValidDateRange {

    String message() default "End date must not be before the start date.";

    String start() default "startDate";

    String end() default "endDate";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
