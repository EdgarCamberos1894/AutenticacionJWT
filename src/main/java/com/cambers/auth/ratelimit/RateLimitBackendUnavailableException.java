package com.cambers.auth.ratelimit;

public class RateLimitBackendUnavailableException extends RuntimeException {

    public RateLimitBackendUnavailableException(Throwable cause) {
        super("Rate-limit backend is unavailable", cause);
    }
}
