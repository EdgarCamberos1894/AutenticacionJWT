package com.cambers.auth.email;

import java.time.Instant;
import java.util.UUID;

public record VerificationTokenIssuedEvent(
        UUID issuanceId,
        String email,
        String rawToken,
        Instant expiresAt
) {
}
