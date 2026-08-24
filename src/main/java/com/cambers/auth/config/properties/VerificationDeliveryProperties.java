package com.cambers.auth.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties(prefix = "auth.email-verification")
public record VerificationDeliveryProperties(
        @NotNull URI publicUrl,
        @NotBlank String from
) {
}
