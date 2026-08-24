package com.cambers.auth.service;

import com.cambers.auth.dto.SessionResponse;
import com.cambers.auth.entity.AuthSession;
import com.cambers.auth.entity.SessionRevocationReason;
import com.cambers.auth.repository.AuthSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SessionManagementService {

    private final AuthSessionRepository authSessionRepository;
    private final SessionRevocationService sessionRevocationService;
    private final Clock clock;

    public SessionManagementService(
            AuthSessionRepository authSessionRepository,
            SessionRevocationService sessionRevocationService,
            Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.sessionRevocationService = sessionRevocationService;
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

    public void logoutCurrent(UUID userId, UUID sessionId) {
        sessionRevocationService.revokeOwnedSession(userId, sessionId, SessionRevocationReason.LOGOUT);
    }

    public void revokeSession(UUID userId, UUID sessionId) {
        sessionRevocationService.revokeOwnedSession(userId, sessionId, SessionRevocationReason.MANUAL_REVOCATION);
    }

    public void logoutAll(UUID userId) {
        sessionRevocationService.revokeAllForUser(userId, SessionRevocationReason.LOGOUT_ALL);
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
