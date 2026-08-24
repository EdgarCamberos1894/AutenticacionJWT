package com.cambers.auth.email;

import java.time.Instant;
import java.util.UUID;

public record PasswordResetTokenIssuedEvent(
        UUID issuanceId,
        String email,
        String rawToken,
        Instant expiresAt
) {
}
