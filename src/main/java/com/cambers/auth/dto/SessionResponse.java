package com.cambers.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID sessionId,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        String userAgent,
        String ipAddress,
        boolean current
) {
}
