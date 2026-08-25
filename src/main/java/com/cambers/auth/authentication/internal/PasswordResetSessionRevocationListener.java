package com.cambers.auth.authentication.internal;

import com.cambers.auth.account.PasswordResetCompleted;
import com.cambers.auth.authentication.internal.model.SessionRevocationReason;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class PasswordResetSessionRevocationListener {
    private final SessionRevocationService sessionRevocationService;
    PasswordResetSessionRevocationListener(SessionRevocationService sessionRevocationService) {
        this.sessionRevocationService = sessionRevocationService;
    }
    @EventListener
    void on(PasswordResetCompleted event) {
        sessionRevocationService.revokeAllForUser(event.userId(), SessionRevocationReason.PASSWORD_RESET);
    }
}
