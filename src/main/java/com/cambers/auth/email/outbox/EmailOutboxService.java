package com.cambers.auth.email.outbox;

import com.cambers.auth.email.AuthenticationEmailComposer;
import com.cambers.auth.email.TransactionalEmail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class EmailOutboxService {

    private final AuthenticationEmailComposer composer;
    private final EmailOutboxCrypto crypto;
    private final EmailOutboxRepository repository;

    public EmailOutboxService(
            AuthenticationEmailComposer composer,
            EmailOutboxCrypto crypto,
            EmailOutboxRepository repository) {
        this.composer = composer;
        this.crypto = crypto;
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueVerification(
            UUID issuanceId,
            String recipient,
            String rawToken,
            Instant expiresAt,
            Instant now) {
        enqueue(
                issuanceId,
                EmailOutboxPurpose.EMAIL_VERIFICATION,
                composer.verification(recipient, rawToken, expiresAt, issuanceId),
                expiresAt,
                now
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueuePasswordReset(
            UUID issuanceId,
            String recipient,
            String rawToken,
            Instant expiresAt,
            Instant now) {
        enqueue(
                issuanceId,
                EmailOutboxPurpose.PASSWORD_RESET,
                composer.passwordReset(recipient, rawToken, expiresAt, issuanceId),
                expiresAt,
                now
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelSuperseded(UUID issuanceId, Instant now) {
        repository.findByIdForUpdate(issuanceId)
                .filter(EmailOutboxMessage::isCancellable)
                .ifPresent(message -> message.cancel("TOKEN_SUPERSEDED", now));
    }

    private void enqueue(
            UUID issuanceId,
            EmailOutboxPurpose purpose,
            TransactionalEmail email,
            Instant expiresAt,
            Instant now) {
        EncryptedEmailPayload payload = crypto.encrypt(issuanceId, purpose, email);
        repository.save(new EmailOutboxMessage(issuanceId, purpose, payload, now, expiresAt));
    }
}
