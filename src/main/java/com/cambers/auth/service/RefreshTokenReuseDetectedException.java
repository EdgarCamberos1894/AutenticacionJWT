package com.cambers.auth.service;

import java.util.UUID;

public class RefreshTokenReuseDetectedException extends RuntimeException {

    private final UUID sessionId;

    public RefreshTokenReuseDetectedException(UUID sessionId) {
        super("Refresh token reuse detected");
        this.sessionId = sessionId;
    }

    public UUID sessionId() {
        return sessionId;
    }
}
