package com.cambers.auth.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiException {

    public UnauthorizedException(ProblemCode code, String detail) {
        super(HttpStatus.UNAUTHORIZED, code, detail);
    }
}
