package com.cambers.auth.authentication.internal.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTokenTtl,
        @Valid Keys keys
) {
    public JwtProperties {
        if (accessTokenTtl != null && (accessTokenTtl.isZero() || accessTokenTtl.isNegative())) {
            throw new IllegalArgumentException("security.jwt.access-token-ttl must be positive");
        }
    }

    public record Keys(
            String publicKeyLocation,
            String privateKeyLocation,
            List<String> previousPublicKeyLocations
    ) {
        public Keys {
            previousPublicKeyLocations = previousPublicKeyLocations == null
                    ? List.of()
                    : previousPublicKeyLocations.stream()
                            .filter(location -> location != null && !location.isBlank())
                            .map(String::trim)
                            .distinct()
                            .toList();
        }
    }
}
