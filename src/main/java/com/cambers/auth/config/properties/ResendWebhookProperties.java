package com.cambers.auth.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "auth.email.resend.webhook")
public record ResendWebhookProperties(
        @NotBlank String signingSecret,
        @NotNull Duration tolerance
) {
    private static final Duration MAX_TOLERANCE = Duration.ofMinutes(10);

    public ResendWebhookProperties {
        if (tolerance != null && (tolerance.isZero() || tolerance.isNegative())) {
            throw new IllegalArgumentException("Resend webhook tolerance must be positive");
        }
        if (tolerance != null && tolerance.compareTo(MAX_TOLERANCE) > 0) {
            throw new IllegalArgumentException("Resend webhook tolerance must not exceed 10 minutes");
        }
    }
}
