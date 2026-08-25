package com.cambers.auth.abuse.internal;

import com.cambers.auth.config.properties.RateLimitProperties;

public interface RequestRateLimiter {

    RateLimitDecision consume(String policyName, String clientIdentifier, RateLimitProperties.Policy policy);
}
