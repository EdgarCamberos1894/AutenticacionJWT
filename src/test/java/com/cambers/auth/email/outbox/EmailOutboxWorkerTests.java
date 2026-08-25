package com.cambers.auth.email.outbox;

import com.cambers.auth.config.properties.EmailOutboxProperties;
import com.cambers.auth.email.EmailDeliveryReceipt;
import com.cambers.auth.email.TransactionalEmail;
import com.cambers.auth.email.TransactionalEmailSender;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailOutboxWorkerTests {

    @Test
    void providerAcceptancePersistenceFailureDoesNotMarkDeliveryAsFailed() {
        EmailOutboxClaimService claimService = mock(EmailOutboxClaimService.class);
        EmailOutboxCrypto crypto = mock(EmailOutboxCrypto.class);
        TransactionalEmailSender sender = mock(TransactionalEmailSender.class);
        EmailOutboxWorker worker = new EmailOutboxWorker(claimService, crypto, sender, properties());
        ClaimedEmailOutboxMessage message = claimedMessage();
        TransactionalEmail email = email(message.id());

        when(claimService.claimNext(anyString()))
                .thenReturn(Optional.of(message))
                .thenReturn(Optional.empty());
        when(crypto.decrypt(message)).thenReturn(email);
        when(sender.send(email)).thenReturn(new EmailDeliveryReceipt("provider-123"));
        when(claimService.markAccepted(message.id(), anyString(), "provider-123"))
                .thenThrow(new IllegalStateException("database unavailable"));

        worker.drain();

        verify(claimService, never()).markFailure(
                org.mockito.ArgumentMatchers.eq(message.id()),
                anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(claimService, never()).reconcileProviderDeliveryStatus(message.id(), "provider-123");
    }

    @Test
    void reconciliationFailureDoesNotTurnAcceptedDeliveryIntoFailure() {
        EmailOutboxClaimService claimService = mock(EmailOutboxClaimService.class);
        EmailOutboxCrypto crypto = mock(EmailOutboxCrypto.class);
        TransactionalEmailSender sender = mock(TransactionalEmailSender.class);
        EmailOutboxWorker worker = new EmailOutboxWorker(claimService, crypto, sender, properties());
        ClaimedEmailOutboxMessage message = claimedMessage();
        TransactionalEmail email = email(message.id());

        when(claimService.claimNext(anyString()))
                .thenReturn(Optional.of(message))
                .thenReturn(Optional.empty());
        when(crypto.decrypt(message)).thenReturn(email);
        when(sender.send(email)).thenReturn(new EmailDeliveryReceipt("provider-123"));
        when(claimService.markAccepted(message.id(), anyString(), "provider-123")).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("temporary reconciliation failure"))
                .when(claimService)
                .reconcileProviderDeliveryStatus(message.id(), "provider-123");

        worker.drain();

        verify(claimService, never()).markFailure(
                org.mockito.ArgumentMatchers.eq(message.id()),
                anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    private ClaimedEmailOutboxMessage claimedMessage() {
        UUID id = UUID.randomUUID();
        return new ClaimedEmailOutboxMessage(
                id,
                EmailOutboxPurpose.EMAIL_VERIFICATION,
                "local-v1",
                new byte[12],
                new byte[32],
                1,
                Instant.now().plusSeconds(600)
        );
    }

    private TransactionalEmail email(UUID id) {
        return new TransactionalEmail(
                "person@example.com",
                "Verify",
                "<p>Verify</p>",
                "Verify",
                "auth/email-verification/" + id,
                List.of()
        );
    }

    private EmailOutboxProperties properties() {
        String key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
        return new EmailOutboxProperties(
                true,
                "local-v1",
                key,
                null,
                null,
                Duration.ofSeconds(2),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Duration.ofMinutes(5),
                20,
                8
        );
    }
}
