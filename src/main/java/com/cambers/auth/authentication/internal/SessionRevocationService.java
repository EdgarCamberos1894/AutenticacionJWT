package com.cambers.auth.authentication.internal;

import com.cambers.auth.entity.AuthSession;
import com.cambers.auth.entity.SessionRevocationReason;
import com.cambers.auth.exception.ResourceNotFoundException;
import com.cambers.auth.repository.AuthSessionRepository;
import com.cambers.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
class SessionRevocationService {

    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    SessionRevocationService(
            AuthSessionRepository authSessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional
    void revokeOwnedSession(UUID userId, UUID sessionId, SessionRevocationReason reason) {
        AuthSession session = authSessionRepository.findByIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found."));
        revokeSessionAndRefreshTokens(session, reason);
    }

    @Transactional
    void revokeAllForUser(UUID userId, SessionRevocationReason reason) {
        Instant now = clock.instant();
        refreshTokenRepository.revokeAllByUserId(userId, now);
        authSessionRepository.revokeAllActiveByUserId(userId, now, reason);
    }

    @Transactional
    void revokeCompromisedSession(UUID sessionId) {
        AuthSession session = authSessionRepository.findByIdForUpdate(sessionId).orElse(null);
        if (session == null) {
            return;
        }
        revokeSessionAndRefreshTokens(session, SessionRevocationReason.REFRESH_TOKEN_REUSE);
    }

    private void revokeSessionAndRefreshTokens(AuthSession session, SessionRevocationReason reason) {
        if (session.isRevoked()) {
            return;
        }

        Instant now = clock.instant();
        session.revoke(now, reason);
        refreshTokenRepository.revokeAllBySessionId(session.getId(), now);
    }
}
