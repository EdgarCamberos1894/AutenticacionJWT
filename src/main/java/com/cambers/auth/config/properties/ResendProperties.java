package com.cambers.auth.config.properties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "auth.email.resend")
public record ResendProperties(
        @NotBlank String apiKey,
        @NotNull URI baseUrl,
        @NotBlank String fromName,
        @NotBlank @Email String fromAddress,
        @Email String replyTo,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
    public ResendProperties {
        if (baseUrl != null && !"https".equalsIgnoreCase(baseUrl.getScheme())) {
            throw new IllegalArgumentException("Resend base URL must use HTTPS");
        }
        if (connectTimeout != null && (connectTimeout.isZero() || connectTimeout.isNegative())) {
            throw new IllegalArgumentException("Resend connect timeout must be positive");
        }
        if (readTimeout != null && (readTimeout.isZero() || readTimeout.isNegative())) {
            throw new IllegalArgumentException("Resend read timeout must be positive");
        }
    }

    public String from() {
        return fromName + " <" + fromAddress + ">";
    }

    public boolean hasReplyTo() {
        return replyTo != null && !replyTo.isBlank();
    }
}
