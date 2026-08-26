package com.cambers.auth.platform;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiException {

    public UnauthorizedException(ProblemCode code, String detail) {
        super(HttpStatus.UNAUTHORIZED, code, detail);
    }
}
