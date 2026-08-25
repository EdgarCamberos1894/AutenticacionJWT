package com.cambers.auth.service;

import com.cambers.auth.dto.AuthSessionResponse;
import com.cambers.auth.entity.AuthSession;
import com.cambers.auth.entity.SessionRevocationReason;
import com.cambers.auth.observability.SecurityAuditAction;
import com.cambers.auth.observability.SecurityAuditEvent;
import com.cambers.auth.observability.SecurityAuditOutcome;
import com.cambers.auth.observability.SecurityAuditPublisher;
import com.cambers.auth.observability.SecurityAuditReason;
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
    private final SecurityAuditPublisher auditPublisher;
    private final Clock clock;

    public SessionManagementService(
            AuthSessionRepository authSessionRepository,
            SessionRevocationService sessionRevocationService,
            SecurityAuditPublisher auditPublisher,
            Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.sessionRevocationService = sessionRevocationService;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AuthSessionResponse> listActiveSessions(UUID accountId, UUID currentSessionId) {
        Instant now = clock.instant();
        return authSessionRepository.findActiveByAccountId(accountId, now)
                .stream()
                .map(session -> toResponse(session, currentSessionId))
                .toList();
    }

    public void logoutCurrent(UUID accountId, UUID sessionId) {
        sessionRevocationService.revokeOwnedSession(accountId, sessionId, SessionRevocationReason.LOGOUT);
        auditRevocation(accountId, sessionId, SecurityAuditReason.LOGOUT);
    }

    public void revokeSession(UUID accountId, UUID sessionId) {
        sessionRevocationService.revokeOwnedSession(accountId, sessionId, SessionRevocationReason.MANUAL_REVOCATION);
        auditRevocation(accountId, sessionId, SecurityAuditReason.MANUAL_REVOCATION);
    }

    public void logoutAll(UUID accountId) {
        sessionRevocationService.revokeAllForAccount(accountId, SessionRevocationReason.LOGOUT_ALL);
        auditRevocation(accountId, null, SecurityAuditReason.LOGOUT_ALL);
    }

    private void auditRevocation(UUID accountId, UUID sessionId, SecurityAuditReason reason) {
        auditPublisher.afterCommit(SecurityAuditEvent.of(
                SecurityAuditAction.SESSION_REVOCATION,
                SecurityAuditOutcome.SUCCESS,
                reason,
                accountId,
                sessionId
        ));
    }

    private AuthSessionResponse toResponse(AuthSession session, UUID currentSessionId) {
        return new AuthSessionResponse(
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
