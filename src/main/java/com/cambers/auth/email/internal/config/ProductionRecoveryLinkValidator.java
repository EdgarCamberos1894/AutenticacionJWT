package com.cambers.auth.email.internal.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
@Profile("prod")
final class ProductionRecoveryLinkValidator {

    ProductionRecoveryLinkValidator(
            VerificationDeliveryProperties verificationProperties,
            PasswordResetDeliveryProperties passwordResetProperties) {
        requireAbsoluteHttpsUrl(
                verificationProperties.publicUrl(),
                "auth.email-verification.public-url"
        );
        requireAbsoluteHttpsUrl(
                passwordResetProperties.publicUrl(),
                "auth.password-reset.public-url"
        );
    }

    private void requireAbsoluteHttpsUrl(URI uri, String propertyName) {
        if (uri == null
                || !uri.isAbsolute()
                || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getHost().isBlank()) {
            throw new IllegalStateException(propertyName + " must be an absolute HTTPS URL in production");
        }
    }
}
