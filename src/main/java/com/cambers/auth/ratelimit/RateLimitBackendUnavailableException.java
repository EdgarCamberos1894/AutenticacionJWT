package com.cambers.auth.ratelimit;

import com.cambers.auth.platform.ApiException;
import com.cambers.auth.platform.ProblemCode;
import com.cambers.auth.platform.RetryAfterProvider;
import org.springframework.http.HttpStatus;

public class RateLimitBackendUnavailableException extends ApiException implements RetryAfterProvider {

    public RateLimitBackendUnavailableException(Throwable cause) {
        super(
                HttpStatus.SERVICE_UNAVAILABLE,
                ProblemCode.SERVICE_UNAVAILABLE,
                "The authentication service is temporarily unable to enforce request limits."
        );
        initCause(cause);
    }

    @Override
    public long retryAfterSeconds() {
        return 1;
    }
}
