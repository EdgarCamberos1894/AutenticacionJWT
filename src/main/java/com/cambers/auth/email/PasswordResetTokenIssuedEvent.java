package com.cambers.auth.email;

import java.time.Instant;

public record PasswordResetTokenIssuedEvent(
        String email,
        String rawToken,
        Instant expiresAt
) {
}
