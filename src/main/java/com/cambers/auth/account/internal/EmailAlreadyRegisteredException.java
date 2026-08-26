package com.cambers.auth.account.internal;

import com.cambers.auth.platform.ApiException;
import com.cambers.auth.platform.ProblemCode;
import org.springframework.http.HttpStatus;

final class EmailAlreadyRegisteredException extends ApiException {

    EmailAlreadyRegisteredException() {
        super(
                HttpStatus.CONFLICT,
                ProblemCode.EMAIL_ALREADY_REGISTERED,
                "An account with this email already exists."
        );
    }
}
