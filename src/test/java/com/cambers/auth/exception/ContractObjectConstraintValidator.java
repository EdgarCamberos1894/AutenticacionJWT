package com.cambers.auth.exception;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Objects;

public final class ContractObjectConstraintValidator
        implements ConstraintValidator<ContractObjectConstraint, ContractTestController.ObjectValidationRequest> {

    @Override
    public boolean isValid(
            ContractTestController.ObjectValidationRequest value,
            ConstraintValidatorContext context) {
        return value == null || Objects.equals(value.first(), value.second());
    }
}
