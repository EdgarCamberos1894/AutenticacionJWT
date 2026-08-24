package com.cambers.auth.email;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@Profile("prod")
public class PasswordResetEmailDelivery implements PasswordResetTokenDelivery {

    private final AuthenticationEmailComposer composer;
    private final TransactionalEmailSender sender;

    public PasswordResetEmailDelivery(AuthenticationEmailComposer composer, TransactionalEmailSender sender) {
        this.composer = composer;
        this.sender = sender;
    }

    @Override
    public void deliver(String email, String rawToken, Instant expiresAt, UUID issuanceId) {
        sender.send(composer.passwordReset(email, rawToken, expiresAt, issuanceId));
    }
}
