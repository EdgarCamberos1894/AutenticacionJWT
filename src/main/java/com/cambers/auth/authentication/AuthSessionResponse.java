package com.cambers.auth.authentication;

import java.time.Instant;
import java.util.UUID;

public record AuthSessionResponse(
        UUID sessionId,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        String userAgent,
        String ipAddress,
        boolean current
) {
}
