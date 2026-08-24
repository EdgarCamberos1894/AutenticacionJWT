package com.cambers.auth.email;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@Profile("prod")
public class EmailVerificationDelivery implements VerificationTokenDelivery {

    private final AuthenticationEmailComposer composer;
    private final TransactionalEmailSender sender;

    public EmailVerificationDelivery(AuthenticationEmailComposer composer, TransactionalEmailSender sender) {
        this.composer = composer;
        this.sender = sender;
    }

    @Override
    public void deliver(String email, String rawToken, Instant expiresAt, UUID issuanceId) {
        sender.send(composer.verification(email, rawToken, expiresAt, issuanceId));
    }
}
