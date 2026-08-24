package com.cambers.auth.service;

import com.cambers.auth.entity.AuthSession;
import com.cambers.auth.repository.AuthSessionRepository;
import com.cambers.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class SessionRevocationService {

    public static final String REFRESH_TOKEN_REUSE_REASON = "REFRESH_TOKEN_REUSE";

    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public SessionRevocationService(
            AuthSessionRepository authSessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional
    public void revokeCompromisedSession(UUID sessionId) {
        AuthSession session = authSessionRepository.findByIdForUpdate(sessionId).orElse(null);
        if (session == null) {
            return;
        }

        Instant now = clock.instant();
        session.revoke(now, REFRESH_TOKEN_REUSE_REASON);
        refreshTokenRepository.revokeAllBySessionId(sessionId, now);
    }
}
