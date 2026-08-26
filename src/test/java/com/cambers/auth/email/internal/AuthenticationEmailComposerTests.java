package com.cambers.auth.email.internal;

import com.cambers.auth.email.internal.config.AuthenticationEmailProperties;
import com.cambers.auth.email.internal.config.PasswordResetDeliveryProperties;
import com.cambers.auth.email.internal.config.VerificationDeliveryProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationEmailComposerTests {

    private final AuthenticationEmailComposer composer = new AuthenticationEmailComposer(
            new AuthenticationEmailProperties("Cambers Auth"),
            new VerificationDeliveryProperties(URI.create("https://app.example.com/verify-email?source=auth")),
            new PasswordResetDeliveryProperties(URI.create("https://app.example.com/reset-password"))
    );

    @Test
    void verificationEmailUsesSafeEncodedLinkTextFallbackAndIssuanceScopedIdempotency() {
        UUID issuanceId = UUID.fromString("4f236fbe-3bc2-42d9-9495-c7d6519f5977");
        Instant expiresAt = Instant.parse("2026-08-25T00:30:00Z");

        TransactionalEmail email = composer.verification(
                "person@example.com",
                "token/value+with special chars",
                expiresAt,
                issuanceId
        );

        assertThat(email.idempotencyKey()).isEqualTo("auth/email-verification/" + issuanceId);
        assertThat(email.tags()).containsExactly(new EmailTag("category", "email_verification"));
        assertThat(email.html()).contains("Cambers Auth", "Verify your email address", "source=auth");
        assertThat(email.html()).doesNotContain("token/value+with special chars");
        assertThat(email.text()).contains("https://app.example.com/verify-email?source=auth&token=");
        assertThat(email.text()).contains("2026-08-25T00:30:00Z");
    }

    @Test
    void passwordResetEmailUsesIndependentCategoryAndIdempotencyNamespace() {
        UUID issuanceId = UUID.fromString("3990dc2f-69f3-4915-b945-c3081ac79b55");

        TransactionalEmail email = composer.passwordReset(
                "person@example.com",
                "reset-token",
                Instant.parse("2026-08-25T00:15:00Z"),
                issuanceId
        );

        assertThat(email.idempotencyKey()).isEqualTo("auth/password-reset/" + issuanceId);
        assertThat(email.tags()).containsExactly(new EmailTag("category", "password_reset"));
        assertThat(email.subject()).isEqualTo("Reset your password");
        assertThat(email.html()).contains("Reset password", "If you did not request a password reset");
        assertThat(email.text()).contains("https://app.example.com/reset-password?token=reset-token");
    }
}
