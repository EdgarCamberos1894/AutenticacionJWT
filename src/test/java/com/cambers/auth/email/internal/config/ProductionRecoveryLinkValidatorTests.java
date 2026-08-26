package com.cambers.auth.email.internal.config;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionRecoveryLinkValidatorTests {

    @Test
    void acceptsAbsoluteHttpsRecoveryLinks() {
        VerificationDeliveryProperties verification = new VerificationDeliveryProperties(
                URI.create("https://app.example.com/verify-email")
        );
        PasswordResetDeliveryProperties passwordReset = new PasswordResetDeliveryProperties(
                URI.create("https://app.example.com/reset-password?source=auth")
        );

        assertThatCode(() -> new ProductionRecoveryLinkValidator(verification, passwordReset))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsHttpVerificationLink() {
        VerificationDeliveryProperties verification = new VerificationDeliveryProperties(
                URI.create("http://app.example.com/verify-email")
        );
        PasswordResetDeliveryProperties passwordReset = new PasswordResetDeliveryProperties(
                URI.create("https://app.example.com/reset-password")
        );

        assertThatThrownBy(() -> new ProductionRecoveryLinkValidator(verification, passwordReset))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.email-verification.public-url");
    }

    @Test
    void rejectsRelativePasswordResetLink() {
        VerificationDeliveryProperties verification = new VerificationDeliveryProperties(
                URI.create("https://app.example.com/verify-email")
        );
        PasswordResetDeliveryProperties passwordReset = new PasswordResetDeliveryProperties(
                URI.create("/reset-password")
        );

        assertThatThrownBy(() -> new ProductionRecoveryLinkValidator(verification, passwordReset))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.password-reset.public-url");
    }
}
