package com.cambers.auth.authentication.internal;

import com.cambers.auth.authentication.AuthSessionResponse;
import com.cambers.auth.authentication.SessionManagement;
import com.cambers.auth.authentication.internal.model.*;
import com.cambers.auth.authentication.internal.persistence.AuthSessionRepository;
import com.cambers.auth.observability.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
class SessionManagementService implements SessionManagement {
    private final AuthSessionRepository authSessionRepository;
    private final SessionRevocationService sessionRevocationService;
    private final SecurityAuditPublisher auditPublisher;
    private final Clock clock;

    SessionManagementService(AuthSessionRepository authSessionRepository, SessionRevocationService sessionRevocationService,
                             SecurityAuditPublisher auditPublisher, Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.sessionRevocationService = sessionRevocationService;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Override @Transactional(readOnly = true)
    public List<AuthSessionResponse> listActiveSessions(UUID userId, UUID currentSessionId) {
        Instant now = clock.instant();
        return authSessionRepository.findActiveByAccountId(userId, now).stream()
                .map(session -> toResponse(session, currentSessionId)).toList();
    }
    @Override public void logoutCurrent(UUID userId, UUID sessionId) {
        sessionRevocationService.revokeOwnedSession(userId, sessionId, SessionRevocationReason.LOGOUT);
        auditRevocation(userId, sessionId, SecurityAuditReason.LOGOUT);
    }
    @Override public void revokeSession(UUID userId, UUID sessionId) {
        sessionRevocationService.revokeOwnedSession(userId, sessionId, SessionRevocationReason.MANUAL_REVOCATION);
        auditRevocation(userId, sessionId, SecurityAuditReason.MANUAL_REVOCATION);
    }
    @Override public void logoutAll(UUID userId) {
        sessionRevocationService.revokeAllForUser(userId, SessionRevocationReason.LOGOUT_ALL);
        auditRevocation(userId, null, SecurityAuditReason.LOGOUT_ALL);
    }
    private void auditRevocation(UUID userId, UUID sessionId, SecurityAuditReason reason) {
        auditPublisher.afterCommit(SecurityAuditEvent.of(SecurityAuditAction.SESSION_REVOCATION,
                SecurityAuditOutcome.SUCCESS, reason, userId, sessionId));
    }
    private AuthSessionResponse toResponse(AuthSession session, UUID currentSessionId) {
        return new AuthSessionResponse(session.getId(), session.getCreatedAt(), session.getLastUsedAt(),
                session.getExpiresAt(), session.getUserAgent(), session.getIpAddress(), session.getId().equals(currentSessionId));
    }
}
