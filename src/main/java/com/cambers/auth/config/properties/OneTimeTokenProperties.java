package com.cambers.auth.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "security.one-time-tokens")
public record OneTimeTokenProperties(
        @NotNull Duration emailVerificationTtl,
        @NotNull Duration passwordResetTtl
) {
    public OneTimeTokenProperties {
        requirePositive(emailVerificationTtl, "security.one-time-tokens.email-verification-ttl");
        requirePositive(passwordResetTtl, "security.one-time-tokens.password-reset-ttl");
    }

    private static void requirePositive(Duration duration, String property) {
        if (duration != null && (duration.isZero() || duration.isNegative())) {
            throw new IllegalArgumentException(property + " must be positive");
        }
    }
}
