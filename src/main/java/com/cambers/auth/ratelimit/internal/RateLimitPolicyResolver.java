package com.cambers.auth.ratelimit.internal;

import java.util.Optional;

class RateLimitPolicyResolver {

    private final RateLimitProperties properties;

    RateLimitPolicyResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    Optional<NamedPolicy> resolve(String requestPath) {
        return switch (requestPath) {
            case "/api/v1/auth/register" -> named("register", properties.register());
            case "/api/v1/auth/login" -> named("login", properties.login());
            case "/api/v1/auth/refresh" -> named("refresh", properties.refresh());
            case "/api/v1/auth/email-verification" -> named("email-verification", properties.emailVerification());
            case "/api/v1/auth/email-verification/confirm" ->
                    named("email-verification-confirm", properties.emailVerificationConfirm());
            case "/api/v1/auth/password-reset" -> named("password-reset", properties.passwordReset());
            case "/api/v1/auth/password-reset/confirm" ->
                    named("password-reset-confirm", properties.passwordResetConfirm());
            default -> Optional.empty();
        };
    }

    private Optional<NamedPolicy> named(String name, RateLimitProperties.Policy policy) {
        return Optional.of(new NamedPolicy(name, policy));
    }

    record NamedPolicy(String name, RateLimitProperties.Policy policy) {
    }
}
