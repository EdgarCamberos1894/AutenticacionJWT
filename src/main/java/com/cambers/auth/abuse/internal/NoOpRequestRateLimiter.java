package com.cambers.auth.abuse.internal;

import com.cambers.auth.config.properties.RateLimitProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "security.rate-limit", name = "enabled", havingValue = "false")
class NoOpRequestRateLimiter implements RequestRateLimiter {

    @Override
    public RateLimitDecision consume(
            String policyName,
            String clientIdentifier,
            RateLimitProperties.Policy policy) {
        return RateLimitDecision.allow();
    }
}
