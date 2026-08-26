package com.cambers.auth.email.internal.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties(prefix = "auth.email-verification")
public record VerificationDeliveryProperties(@NotNull URI publicUrl) {
}
