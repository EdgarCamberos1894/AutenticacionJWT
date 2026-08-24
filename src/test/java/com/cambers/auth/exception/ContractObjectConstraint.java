package com.cambers.auth.exception;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ContractObjectConstraintValidator.class)
@interface ContractObjectConstraint {

    String message() default "first and second must match";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
