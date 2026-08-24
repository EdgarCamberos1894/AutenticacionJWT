package com.cambers.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        UUID sessionId
) {
}
