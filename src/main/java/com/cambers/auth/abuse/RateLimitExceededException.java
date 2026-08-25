package com.cambers.auth.abuse;

import com.cambers.auth.exception.ApiException;
import com.cambers.auth.exception.ProblemCode;
import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(
                HttpStatus.TOO_MANY_REQUESTS,
                ProblemCode.RATE_LIMIT_EXCEEDED,
                "Too many requests. Retry after the indicated delay."
        );
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
