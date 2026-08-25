package com.cambers.auth.abuse;

import com.cambers.auth.abuse.internal.RateLimitDecision;
import com.cambers.auth.abuse.internal.RequestRateLimiter;
import com.cambers.auth.config.properties.RateLimitProperties;
import org.springframework.stereotype.Service;

@Service
public class LoginRateLimitService {

    private static final String ACCOUNT_POLICY_NAME = "login-account";

    private final RequestRateLimiter requestRateLimiter;
    private final RateLimitProperties properties;

    public LoginRateLimitService(
            RequestRateLimiter requestRateLimiter,
            RateLimitProperties properties) {
        this.requestRateLimiter = requestRateLimiter;
        this.properties = properties;
    }

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
