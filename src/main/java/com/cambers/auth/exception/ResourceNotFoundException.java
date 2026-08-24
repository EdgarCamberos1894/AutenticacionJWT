package com.cambers.auth.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, ProblemCode.RESOURCE_NOT_FOUND, detail);
    }
}
