package com.cambers.auth.service;

import com.cambers.auth.account.AccountPasswordChanged;
import com.cambers.auth.entity.SessionRevocationReason;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps session invalidation coupled to the account password-change transaction
 * without introducing an account-to-authentication module dependency.
 */
@Component
class AccountPasswordChangedSessionRevocationListener {

    private final SessionRevocationService sessionRevocationService;

    AccountPasswordChangedSessionRevocationListener(SessionRevocationService sessionRevocationService) {
        this.sessionRevocationService = sessionRevocationService;
    }

    @EventListener
    void on(AccountPasswordChanged event) {
        sessionRevocationService.revokeAllForAccount(
                event.accountId(),
                SessionRevocationReason.PASSWORD_RESET
        );
    }
}
