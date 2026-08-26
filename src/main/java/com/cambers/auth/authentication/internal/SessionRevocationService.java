package com.cambers.auth.authentication.internal;

import com.cambers.auth.authentication.internal.model.*;
import com.cambers.auth.authentication.internal.persistence.*;
import com.cambers.auth.platform.ResourceNotFoundException;
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

    SessionRevocationService(AuthSessionRepository authSessionRepository, RefreshTokenRepository refreshTokenRepository, Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional
    void revokeOwnedSession(UUID accountId, UUID sessionId, SessionRevocationReason reason) {
        AuthSession session = authSessionRepository.findByIdAndAccountIdForUpdate(sessionId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found."));
        revokeSessionAndRefreshTokens(session, reason);
    }

    @Transactional
    void revokeAllForUser(UUID accountId, SessionRevocationReason reason) {
        Instant now = clock.instant();
        refreshTokenRepository.revokeAllByAccountId(accountId, now);
        authSessionRepository.revokeAllActiveByAccountId(accountId, now, reason);
    }

    @Transactional
    void revokeCompromisedSession(UUID sessionId) {
        AuthSession session = authSessionRepository.findByIdForUpdate(sessionId).orElse(null);
        if (session != null) revokeSessionAndRefreshTokens(session, SessionRevocationReason.REFRESH_TOKEN_REUSE);
    }

    private void revokeSessionAndRefreshTokens(AuthSession session, SessionRevocationReason reason) {
        if (session.isRevoked()) return;
        Instant now = clock.instant();
        session.revoke(now, reason);
        refreshTokenRepository.revokeAllBySessionId(session.getId(), now);
    }
}
