package com.cambers.auth.platform;

import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ProblemCode code;

    protected ApiException(HttpStatus status, ProblemCode code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public ProblemCode code() {
        return code;
    }
}
