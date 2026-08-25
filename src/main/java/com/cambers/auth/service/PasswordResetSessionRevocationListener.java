package com.cambers.auth.service;

import com.cambers.auth.account.PasswordResetCompleted;
import com.cambers.auth.entity.SessionRevocationReason;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetSessionRevocationListener {

    private final SessionRevocationService sessionRevocationService;

    public PasswordResetSessionRevocationListener(SessionRevocationService sessionRevocationService) {
        this.sessionRevocationService = sessionRevocationService;
    }

    @EventListener
    public void on(PasswordResetCompleted event) {
        sessionRevocationService.revokeAllForUser(
                event.userId(),
                SessionRevocationReason.PASSWORD_RESET
        );
    }
}
