package com.cambers.auth.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "security.sessions")
public record SessionProperties(
        @NotNull Duration sessionTtl,
        @NotNull Duration refreshTokenTtl
) {
    public SessionProperties {
        if (sessionTtl != null && (sessionTtl.isZero() || sessionTtl.isNegative())) {
            throw new IllegalArgumentException("security.sessions.session-ttl must be positive");
        }
        if (refreshTokenTtl != null && (refreshTokenTtl.isZero() || refreshTokenTtl.isNegative())) {
            throw new IllegalArgumentException("security.sessions.refresh-token-ttl must be positive");
        }
        if (sessionTtl != null && refreshTokenTtl != null && refreshTokenTtl.compareTo(sessionTtl) > 0) {
            throw new IllegalArgumentException("security.sessions.refresh-token-ttl must not exceed session-ttl");
        }
    }
}
