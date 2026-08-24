package com.cambers.auth.service;

import com.cambers.auth.dto.SessionResponse;
import com.cambers.auth.entity.AuthSession;
import com.cambers.auth.exception.ResourceNotFoundException;
import com.cambers.auth.repository.AuthSessionRepository;
import com.cambers.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SessionManagementService {

    private static final String LOGOUT_REASON = "LOGOUT";
    private static final String LOGOUT_ALL_REASON = "LOGOUT_ALL";
    private static final String MANUAL_REVOCATION_REASON = "MANUAL_REVOCATION";
    private static final String PASSWORD_RESET_REASON = "PASSWORD_RESET";

    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public SessionManagementService(
            AuthSessionRepository authSessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listActiveSessions(UUID userId, UUID currentSessionId) {
        Instant now = clock.instant();
        return authSessionRepository.findActiveByUserId(userId, now)
                .stream()
                .map(session -> toResponse(session, currentSessionId))
                .toList();
    }

    @Transactional
    public void logoutCurrent(UUID userId, UUID sessionId) {
        revokeOwnedSession(userId, sessionId, LOGOUT_REASON);
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        revokeOwnedSession(userId, sessionId, MANUAL_REVOCATION_REASON);
    }

    @Transactional
    public void logoutAll(UUID userId) {
        revokeAll(userId, LOGOUT_ALL_REASON);
    }

    @Transactional
    public void revokeAllForPasswordReset(UUID userId) {
        revokeAll(userId, PASSWORD_RESET_REASON);
    }

    private void revokeOwnedSession(UUID userId, UUID sessionId, String reason) {
        AuthSession session = authSessionRepository.findByIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found."));

        if (session.isRevoked()) {
            return;
        }

        Instant now = clock.instant();
        session.revoke(now, reason);
        refreshTokenRepository.revokeAllBySessionId(sessionId, now);
    }

    private void revokeAll(UUID userId, String reason) {
        Instant now = clock.instant();
        refreshTokenRepository.revokeAllByUserId(userId, now);
        authSessionRepository.revokeAllActiveByUserId(userId, now, reason);
    }

    private SessionResponse toResponse(AuthSession session, UUID currentSessionId) {
        return new SessionResponse(
                session.getId(),
                session.getCreatedAt(),
                session.getLastUsedAt(),
                session.getExpiresAt(),
                session.getUserAgent(),
                session.getIpAddress(),
                session.getId().equals(currentSessionId)
        );
    }
}
