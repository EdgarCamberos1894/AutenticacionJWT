package com.cambers.auth.authentication;

import java.time.Instant;
import java.util.UUID;

public record TokenPairResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        UUID sessionId
) {
}
