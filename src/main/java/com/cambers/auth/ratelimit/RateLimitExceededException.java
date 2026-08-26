package com.cambers.auth.ratelimit;

import com.cambers.auth.platform.ApiException;
import com.cambers.auth.platform.ProblemCode;
import com.cambers.auth.platform.RetryAfterProvider;
import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends ApiException implements RetryAfterProvider {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(
                HttpStatus.TOO_MANY_REQUESTS,
                ProblemCode.RATE_LIMIT_EXCEEDED,
                "Too many requests. Retry after the indicated delay."
        );
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    @Override
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
