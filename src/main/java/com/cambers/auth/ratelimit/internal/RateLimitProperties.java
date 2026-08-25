package com.cambers.auth.ratelimit.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "security.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        @NotNull @Valid Policy register,
        @NotNull @Valid Policy login,
        @NotNull @Valid Policy loginAccount,
        @NotNull @Valid Policy refresh,
        @NotNull @Valid Policy emailVerification,
        @NotNull @Valid Policy emailVerificationConfirm,
        @NotNull @Valid Policy passwordReset,
        @NotNull @Valid Policy passwordResetConfirm
) {
    public record Policy(@Min(1) int limit, @NotNull Duration window) {
        public Policy {
            if (window != null && (window.isZero() || window.isNegative())) {
                throw new IllegalArgumentException("Rate-limit windows must be positive");
            }
        }
    }
}
