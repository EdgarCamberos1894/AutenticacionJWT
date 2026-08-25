package com.cambers.auth.ratelimit.internal;

record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

    static RateLimitDecision allow() {
        return new RateLimitDecision(true, 0);
    }

    static RateLimitDecision reject(long retryAfterSeconds) {
        return new RateLimitDecision(false, Math.max(1, retryAfterSeconds));
    }
}
