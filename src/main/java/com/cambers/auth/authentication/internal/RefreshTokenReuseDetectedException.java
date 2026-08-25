package com.cambers.auth.authentication.internal;

import java.util.UUID;

final class RefreshTokenReuseDetectedException extends RuntimeException {

    private final UUID sessionId;

    RefreshTokenReuseDetectedException(UUID sessionId) {
        super("Refresh token reuse detected");
        this.sessionId = sessionId;
    }

    UUID sessionId() {
        return sessionId;
    }
}
