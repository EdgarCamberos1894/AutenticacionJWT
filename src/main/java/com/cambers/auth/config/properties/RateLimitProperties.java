package com.cambers.auth.config.properties;

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
        @Valid Policy register,
        @Valid Policy login,
        @Valid Policy refresh,
        @Valid Policy emailVerification,
        @Valid Policy emailVerificationConfirm,
        @Valid Policy passwordReset,
        @Valid Policy passwordResetConfirm
) {
    public record Policy(@Min(1) int limit, @NotNull Duration window) {
        public Policy {
            if (window != null && (window.isZero() || window.isNegative())) {
                throw new IllegalArgumentException("Rate-limit windows must be positive");
            }
        }
    }
}
