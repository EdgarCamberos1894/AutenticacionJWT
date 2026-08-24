package com.cambers.auth.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String detail) {
        super(HttpStatus.CONFLICT, ProblemCode.CONFLICT, detail);
    }
}
