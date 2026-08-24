package com.cambers.auth.email;

import java.time.Instant;

public record VerificationTokenIssuedEvent(
        String email,
        String rawToken,
        Instant expiresAt
) {
}
