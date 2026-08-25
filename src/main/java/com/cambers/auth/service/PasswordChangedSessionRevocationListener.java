package com.cambers.auth.service;

import com.cambers.auth.account.PasswordChangedEvent;
import com.cambers.auth.entity.SessionRevocationReason;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class PasswordChangedSessionRevocationListener {

    private final SessionRevocationService sessionRevocationService;

    PasswordChangedSessionRevocationListener(SessionRevocationService sessionRevocationService) {
        this.sessionRevocationService = sessionRevocationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void onPasswordChanged(PasswordChangedEvent event) {
        sessionRevocationService.revokeAllForUser(
                event.accountId(),
                SessionRevocationReason.PASSWORD_RESET
        );
    }
}
