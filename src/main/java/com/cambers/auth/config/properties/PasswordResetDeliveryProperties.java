package com.cambers.auth.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties(prefix = "auth.password-reset")
public record PasswordResetDeliveryProperties(@NotNull URI publicUrl) {
}
