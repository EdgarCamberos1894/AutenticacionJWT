package com.cambers.auth.platform;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

    public BadRequestException(ProblemCode code, String detail) {
        super(HttpStatus.BAD_REQUEST, code, detail);
    }
}
