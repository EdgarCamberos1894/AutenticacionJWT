package com.cambers.auth.email.internal.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth.email")
public record AuthenticationEmailProperties(@NotBlank String productName) {
}
