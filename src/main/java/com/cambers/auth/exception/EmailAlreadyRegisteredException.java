package com.cambers.auth.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyRegisteredException extends ApiException {

    public EmailAlreadyRegisteredException() {
        super(
                HttpStatus.CONFLICT,
                ProblemCode.EMAIL_ALREADY_REGISTERED,
                "An account with this email already exists."
        );
    }
}
