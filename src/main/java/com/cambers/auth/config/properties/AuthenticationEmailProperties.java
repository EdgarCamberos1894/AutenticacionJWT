package com.cambers.auth.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth.email")
public record AuthenticationEmailProperties(@NotBlank String productName) {
}
