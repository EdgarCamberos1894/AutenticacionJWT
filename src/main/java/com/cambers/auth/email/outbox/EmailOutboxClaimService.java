package com.cambers.auth.email.outbox;

import com.cambers.auth.config.properties.EmailOutboxProperties;
import com.cambers.auth.email.resend.ResendEmailEventMapper;
import com.cambers.auth.email.resend.ResendWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmailOutboxClaimService {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxClaimService.class);

    private final EmailOutboxRepository repository;
    private final ResendWebhookEventRepository webhookEventRepository;
    private final ResendEmailEventMapper eventMapper;
    private final EmailOutboxProperties properties;
    private final Clock clock;

    public EmailOutboxClaimService(
            EmailOutboxRepository repository,
            ResendWebhookEventRepository webhookEventRepository,
            ResendEmailEventMapper eventMapper,
            EmailOutboxProperties properties,
            Clock clock) {
        this.repository = repository;
        this.webhookEventRepository = webhookEventRepository;
        this.eventMapper = eventMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedEmailOutboxMessage> claimNext(String workerId) {
        Instant now = clock.instant();
        repository.expireMessages(now);
        Optional<UUID> id = repository.findNextClaimableId(now, now.minus(properties.leaseDuration()));
        if (id.isEmpty()) {
            return Optional.empty();
        }

        EmailOutboxMessage message = repository.findById(id.get())
                .orElseThrow(() -> new IllegalStateException("Claimed email outbox message disappeared"));
        message.claim(workerId, now);
        return Optional.of(snapshot(message));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAccepted(UUID messageId, String workerId, String providerMessageId) {
        EmailOutboxMessage message = repository.findById(messageId)
                .orElseThrow(() -> new IllegalStateException("Email outbox message disappeared before completion"));
        if (!message.isOwnedBy(workerId)) {
            log.warn("Ignoring stale email outbox completion messageId={}", messageId);
            return;
        }

        Instant now = clock.instant();
        message.markAccepted(providerMessageId, now);
        webhookEventRepository.findTopByProviderMessageIdOrderByEventCreatedAtDesc(providerMessageId)
                .ifPresent(event -> eventMapper.deliveryStatus(event.getEventType())
                        .ifPresent(status -> message.applyDeliveryStatus(
                                status,
                                event.getEventCreatedAt(),
                                now
                        )));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailure(UUID messageId, String workerId, boolean retryable, String errorCode) {
        EmailOutboxMessage message = repository.findById(messageId)
                .orElseThrow(() -> new IllegalStateException("Email outbox message disappeared before failure handling"));
        if (!message.isOwnedBy(workerId)) {
            log.warn("Ignoring stale email outbox failure messageId={}", messageId);
            return;
        }

        Instant now = clock.instant();
        boolean exhausted = message.getAttemptCount() >= properties.maxAttempts();
        if (!retryable || exhausted || !now.isBefore(message.getExpiresAt())) {
            message.markDead(safeErrorCode(errorCode), now);
            return;
        }

        Instant nextAttemptAt = now.plus(backoff(message.getAttemptCount(), messageId));
        if (!nextAttemptAt.isBefore(message.getExpiresAt())) {
            message.markDead("TOKEN_EXPIRES_BEFORE_RETRY", now);
            return;
        }
        message.reschedule(safeErrorCode(errorCode), nextAttemptAt, now);
    }

    private ClaimedEmailOutboxMessage snapshot(EmailOutboxMessage message) {
        return new ClaimedEmailOutboxMessage(
                message.getId(),
                message.getPurpose(),
                message.getKeyId(),
                message.getNonce(),
                message.getCiphertext(),
                message.getAttemptCount(),
                message.getExpiresAt()
        );
    }

    private Duration backoff(int attempt, UUID messageId) {
        long exponent = Math.min(Math.max(attempt - 1, 0), 20);
        Duration delay;
        try {
            delay = properties.baseBackoff().multipliedBy(1L << exponent);
        } catch (ArithmeticException exception) {
            delay = properties.maxBackoff();
        }
        if (delay.compareTo(properties.maxBackoff()) > 0) {
            delay = properties.maxBackoff();
        }
        int jitterPercent = Math.floorMod(messageId.hashCode(), 21);
        Duration jittered = delay.plus(delay.multipliedBy(jitterPercent).dividedBy(100));
        return jittered.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : jittered;
    }

    private String safeErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return "EMAIL_DELIVERY_ERROR";
        }
        return errorCode.length() <= 128 ? errorCode : errorCode.substring(0, 128);
    }
}
