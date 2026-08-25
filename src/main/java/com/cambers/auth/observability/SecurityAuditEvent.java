package com.cambers.auth.observability;

import java.util.Objects;
import java.util.UUID;

public record SecurityAuditEvent(
        SecurityAuditAction action,
        SecurityAuditOutcome outcome,
        SecurityAuditReason reason,
        UUID userId,
        UUID sessionId
) {
    public SecurityAuditEvent {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reason, "reason");
    }

    public static SecurityAuditEvent of(
            SecurityAuditAction action,
            SecurityAuditOutcome outcome,
            SecurityAuditReason reason,
            UUID userId,
            UUID sessionId) {
        return new SecurityAuditEvent(action, outcome, reason, userId, sessionId);
    }
}
