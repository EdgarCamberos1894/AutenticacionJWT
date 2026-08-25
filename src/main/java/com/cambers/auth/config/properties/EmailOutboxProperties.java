package com.cambers.auth.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "auth.email.outbox")
public record EmailOutboxProperties(
        boolean workerEnabled,
        @NotBlank String activeKeyId,
        @NotBlank String activeKey,
        String previousKeyId,
        String previousKey,
        @NotNull Duration pollInterval,
        @NotNull Duration leaseDuration,
        @NotNull Duration baseBackoff,
        @NotNull Duration maxBackoff,
        @Min(1) @Max(100) int batchSize,
        @Min(1) @Max(20) int maxAttempts
) {
    public EmailOutboxProperties {
        requirePositive(pollInterval, "pollInterval");
        requirePositive(leaseDuration, "leaseDuration");
        requirePositive(baseBackoff, "baseBackoff");
        requirePositive(maxBackoff, "maxBackoff");
        if (baseBackoff != null && maxBackoff != null && baseBackoff.compareTo(maxBackoff) > 0) {
            throw new IllegalArgumentException("Email outbox base backoff cannot exceed max backoff");
        }
        boolean hasPreviousId = previousKeyId != null && !previousKeyId.isBlank();
        boolean hasPreviousKey = previousKey != null && !previousKey.isBlank();
        if (hasPreviousId != hasPreviousKey) {
            throw new IllegalArgumentException("Email outbox previous key id and key must be configured together");
        }
        if (hasPreviousId && previousKeyId.equals(activeKeyId)) {
            throw new IllegalArgumentException("Email outbox active and previous key ids must differ");
        }
    }

    public boolean hasPreviousKey() {
        return previousKeyId != null && !previousKeyId.isBlank();
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration != null && (duration.isZero() || duration.isNegative())) {
            throw new IllegalArgumentException("Email outbox " + name + " must be positive");
        }
    }
}
