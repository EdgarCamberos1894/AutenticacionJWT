package com.cambers.auth.ratelimit.internal;

interface RequestRateLimiter {

    RateLimitDecision consume(String policyName, String clientIdentifier, RateLimitProperties.Policy policy);
}
