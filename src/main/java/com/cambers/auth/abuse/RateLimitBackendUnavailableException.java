package com.cambers.auth.abuse;

import com.cambers.auth.exception.ApiException;
import com.cambers.auth.exception.ProblemCode;
import org.springframework.http.HttpStatus;

public class RateLimitBackendUnavailableException extends ApiException {

    public RateLimitBackendUnavailableException(Throwable cause) {
        super(
                HttpStatus.SERVICE_UNAVAILABLE,
                ProblemCode.SERVICE_UNAVAILABLE,
                "The authentication service is temporarily unable to enforce request limits."
        );
        initCause(cause);
    }
}
