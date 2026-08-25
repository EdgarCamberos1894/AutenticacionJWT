package com.cambers.auth.ratelimit;

/**
 * Account-scoped abuse prevention for public account-recovery flows.
 *
 * <p>The identifier is expected to be a normalized email address. Implementations
 * must apply the same policy whether or not an account exists for that address so
 * the limiter does not become an account-enumeration oracle.</p>
 */
public interface RecoveryRateLimitService {

    void checkEmailVerification(String normalizedEmail);

    void checkPasswordReset(String normalizedEmail);
}
