package com.cambers.auth.email.outbox;

import com.cambers.auth.email.internal.EmailTag;
import com.cambers.auth.email.internal.TransactionalEmail;
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
class EmailOutboxProcessingServiceIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired private EmailOutboxRepository outboxRepository;
    @Autowired private EmailOutboxCrypto crypto;
    @Autowired private EmailOutboxProcessingService processingService;

    @BeforeEach
    void cleanDatabase() {
        outboxRepository.deleteAllInBatch();
    }

    @Test
    void activeLeaseCannotBeClaimedByAnotherWorker() {
        EmailOutboxMessage message = pendingMessage();
        outboxRepository.saveAndFlush(message);

        var first = processingService.claimNext("worker-a");
        var second = processingService.claimNext("worker-b");

        assertThat(first).isPresent();
        assertThat(first.orElseThrow().attemptCount()).isEqualTo(1);
        assertThat(second).isEmpty();
    }

    @Test
    void expiredLeaseIsRecoveredAndOldWorkerCanNoLongerComplete() {
        EmailOutboxMessage message = pendingMessage();
        message.claim("dead-worker", Instant.now().minusSeconds(60));
        outboxRepository.saveAndFlush(message);

        var recovered = processingService.claimNext("worker-b").orElseThrow();

        assertThat(recovered.attemptCount()).isEqualTo(2);
        assertThat(processingService.markAccepted(recovered.id(), "dead-worker", "stale-provider-id")).isFalse();

        EmailOutboxMessage stillOwnedByReplacement = outboxRepository.findById(message.getId()).orElseThrow();
        assertThat(stillOwnedByReplacement.getStatus()).isEqualTo(EmailOutboxStatus.PROCESSING);
        assertThat(stillOwnedByReplacement.getProviderMessageId()).isNull();

        assertThat(processingService.markAccepted(recovered.id(), "worker-b", "provider-id")).isTrue();
        EmailOutboxMessage completed = outboxRepository.findById(message.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(completed.getProviderMessageId()).isEqualTo("provider-id");
    }

    @Test
    void retryableFailureReschedulesAndWrongWorkerCannotCompleteMessage() {
        EmailOutboxMessage message = outboxRepository.saveAndFlush(pendingMessage());
        var claimed = processingService.claimNext("worker-a").orElseThrow();

        assertThat(processingService.markAccepted(claimed.id(), "worker-b", "wrong-owner")).isFalse();
        assertThat(outboxRepository.findById(message.getId()).orElseThrow().getStatus())
                .isEqualTo(EmailOutboxStatus.PROCESSING);

        processingService.markFailure(claimed.id(), "worker-a", true, "rate_limit_exceeded");
        EmailOutboxMessage rescheduled = outboxRepository.findById(message.getId()).orElseThrow();
        assertThat(rescheduled.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(rescheduled.getAttemptCount()).isEqualTo(1);
        assertThat(rescheduled.getNextAttemptAt()).isAfter(Instant.now());
    }

    @Test
    void permanentFailureMovesMessageToDeadStateAndScrubsPayload() {
        EmailOutboxMessage message = outboxRepository.saveAndFlush(pendingMessage());
        var claimed = processingService.claimNext("worker-a").orElseThrow();

        processingService.markFailure(claimed.id(), "worker-a", false, "invalid_request");

        EmailOutboxMessage failed = outboxRepository.findById(message.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(EmailOutboxStatus.DEAD);
        assertThat(failed.getDeliveryStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(failed.getNonce()).isNull();
        assertThat(failed.getCiphertext()).isNull();
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
