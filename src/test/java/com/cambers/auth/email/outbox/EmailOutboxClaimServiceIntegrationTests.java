package com.cambers.auth.email.outbox;

import com.cambers.auth.email.EmailTag;
import com.cambers.auth.email.TransactionalEmail;
import com.cambers.auth.email.resend.ResendWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class EmailOutboxClaimServiceIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired private EmailOutboxRepository outboxRepository;
    @Autowired private ResendWebhookEventRepository webhookEventRepository;
    @Autowired private EmailOutboxCrypto crypto;
    @Autowired private EmailOutboxClaimService claimService;

    @BeforeEach
    void cleanDatabase() {
        webhookEventRepository.deleteAllInBatch();
        outboxRepository.deleteAllInBatch();
    }

    @Test
    void activeLeaseCannotBeClaimedByAnotherWorker() {
        EmailOutboxMessage message = pendingMessage();
        outboxRepository.saveAndFlush(message);

        var first = claimService.claimNext("worker-a");
        var second = claimService.claimNext("worker-b");

        assertThat(first).isPresent();
        assertThat(first.orElseThrow().attemptCount()).isEqualTo(1);
        assertThat(second).isEmpty();
    }

    @Test
    void expiredLeaseIsRecoveredByAnotherWorker() {
        EmailOutboxMessage message = pendingMessage();
        message.claim("dead-worker", Instant.now().minusSeconds(60));
        outboxRepository.saveAndFlush(message);

        var recovered = claimService.claimNext("worker-b");

        assertThat(recovered).isPresent();
        assertThat(recovered.orElseThrow().attemptCount()).isEqualTo(2);
    }

    @Test
    void retryableFailureReschedulesAndStaleWorkerCannotCompleteMessage() {
        EmailOutboxMessage message = outboxRepository.saveAndFlush(pendingMessage());
        var claimed = claimService.claimNext("worker-a").orElseThrow();

        claimService.markAccepted(claimed.id(), "worker-b", "wrong-owner");
        assertThat(outboxRepository.findById(message.getId()).orElseThrow().getStatus())
                .isEqualTo(EmailOutboxStatus.PROCESSING);

        claimService.markFailure(claimed.id(), "worker-a", true, "rate_limit_exceeded");
        EmailOutboxMessage rescheduled = outboxRepository.findById(message.getId()).orElseThrow();
        assertThat(rescheduled.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(rescheduled.getAttemptCount()).isEqualTo(1);
        assertThat(rescheduled.getNextAttemptAt()).isAfter(Instant.now());
    }

    @Test
    void permanentFailureMovesMessageToDeadState() {
        EmailOutboxMessage message = outboxRepository.saveAndFlush(pendingMessage());
        var claimed = claimService.claimNext("worker-a").orElseThrow();

        claimService.markFailure(claimed.id(), "worker-a", false, "invalid_request");

        EmailOutboxMessage failed = outboxRepository.findById(message.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(EmailOutboxStatus.DEAD);
        assertThat(failed.getDeliveryStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
    }

    private EmailOutboxMessage pendingMessage() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        TransactionalEmail email = new TransactionalEmail(
                "person@example.com",
                "Verify",
                "<p>Verify</p>",
                "Verify",
                "auth/email-verification/" + id,
                List.of(new EmailTag("category", "email_verification"))
        );
        return new EmailOutboxMessage(
                id,
                EmailOutboxPurpose.EMAIL_VERIFICATION,
                crypto.encrypt(id, EmailOutboxPurpose.EMAIL_VERIFICATION, email),
                now,
                now.plusSeconds(600)
        );
    }
}
