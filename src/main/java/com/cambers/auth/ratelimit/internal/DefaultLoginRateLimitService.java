package com.cambers.auth.ratelimit.internal;

import com.cambers.auth.ratelimit.LoginRateLimitService;
import com.cambers.auth.ratelimit.RateLimitExceededException;
import org.springframework.stereotype.Service;

@Service
class DefaultLoginRateLimitService implements LoginRateLimitService {

    private static final String ACCOUNT_POLICY_NAME = "login-account";

    private final RequestRateLimiter requestRateLimiter;
    private final RateLimitProperties properties;

    DefaultLoginRateLimitService(
            RequestRateLimiter requestRateLimiter,
            RateLimitProperties properties) {
        this.requestRateLimiter = requestRateLimiter;
        this.properties = properties;
    }

    @Override
    public void checkAccount(String normalizedEmail) {
        RateLimitDecision decision = requestRateLimiter.consume(
                ACCOUNT_POLICY_NAME,
                normalizedEmail,
                properties.loginAccount()
        );
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
    }
}
