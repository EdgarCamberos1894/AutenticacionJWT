package com.cambers.auth.ratelimit.internal;

import com.cambers.auth.ratelimit.RateLimitExceededException;
import com.cambers.auth.ratelimit.RecoveryRateLimitService;
import org.springframework.stereotype.Service;

@Service
class DefaultRecoveryRateLimitService implements RecoveryRateLimitService {

    private static final String EMAIL_VERIFICATION_ACCOUNT_POLICY = "email-verification-account";
    private static final String PASSWORD_RESET_ACCOUNT_POLICY = "password-reset-account";

    private final RequestRateLimiter requestRateLimiter;
    private final RateLimitProperties properties;

    DefaultRecoveryRateLimitService(
            RequestRateLimiter requestRateLimiter,
            RateLimitProperties properties) {
        this.requestRateLimiter = requestRateLimiter;
        this.properties = properties;
    }

    @Override
    public void checkEmailVerification(String normalizedEmail) {
        enforce(
                EMAIL_VERIFICATION_ACCOUNT_POLICY,
                normalizedEmail,
                properties.emailVerificationAccount()
        );
    }

    @Override
    public void checkPasswordReset(String normalizedEmail) {
        enforce(
                PASSWORD_RESET_ACCOUNT_POLICY,
                normalizedEmail,
                properties.passwordResetAccount()
        );
    }

    private void enforce(
            String policyName,
            String normalizedEmail,
            RateLimitProperties.Policy policy) {
        RateLimitDecision decision = requestRateLimiter.consume(policyName, normalizedEmail, policy);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
    }
}
