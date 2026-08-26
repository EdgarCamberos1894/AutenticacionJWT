package com.cambers.auth.email.outbox;

import com.cambers.auth.email.internal.EmailDeliveryException;
import com.cambers.auth.email.internal.EmailDeliveryReceipt;
import com.cambers.auth.email.internal.TransactionalEmail;
import com.cambers.auth.email.internal.TransactionalEmailSender;
import com.cambers.auth.email.internal.config.EmailOutboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "auth.email.outbox", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class EmailOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxWorker.class);

    private final String workerId = UUID.randomUUID().toString();
    private final EmailOutboxProcessingService processingService;
    private final EmailOutboxCrypto crypto;
    private final TransactionalEmailSender sender;
    private final EmailOutboxProperties properties;

    public EmailOutboxWorker(
            EmailOutboxProcessingService processingService,
            EmailOutboxCrypto crypto,
            TransactionalEmailSender sender,
            EmailOutboxProperties properties) {
        this.processingService = processingService;
        this.crypto = crypto;
        this.sender = sender;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${auth.email.outbox.poll-interval:PT2S}")
    public void drain() {
        for (int index = 0; index < properties.batchSize(); index++) {
            var claimed = processingService.claimNext(workerId);
            if (claimed.isEmpty()) {
                return;
            }
            deliver(claimed.get());
        }
    }

    private void deliver(ClaimedEmailOutboxMessage message) {
        TransactionalEmail email;
        try {
            email = crypto.decrypt(message);
        } catch (RuntimeException exception) {
            log.error("Could not decrypt transactional email outbox messageId={}", message.id(), exception);
            processingService.markFailure(message.id(), workerId, true, "OUTBOX_DECRYPTION_ERROR");
            return;
        }

        EmailDeliveryReceipt receipt;
        try {
            receipt = sender.send(email);
        } catch (EmailDeliveryException exception) {
            log.warn(
                    "Transactional email delivery failed messageId={} attempt={} retryable={} providerStatus={} providerCode={}",
                    message.id(),
                    message.attemptCount(),
                    exception.isRetryable(),
                    exception.getProviderStatus(),
                    exception.getProviderCode()
            );
            processingService.markFailure(
                    message.id(),
                    workerId,
                    exception.isRetryable(),
                    exception.getProviderCode() != null
                            ? exception.getProviderCode()
                            : providerStatusCode(exception.getProviderStatus())
            );
            return;
        } catch (RuntimeException exception) {
            log.error("Unexpected transactional email provider failure messageId={}", message.id(), exception);
            processingService.markFailure(message.id(), workerId, true, "EMAIL_PROVIDER_RUNTIME_ERROR");
            return;
        }

        boolean accepted;
        try {
            accepted = processingService.markAccepted(
                    message.id(),
                    workerId,
                    receipt.providerMessageId()
            );
        } catch (RuntimeException exception) {
            // The provider already accepted this idempotency key. Keep the lease state intact so a
            // later reclaim can safely retry the same provider operation instead of declaring failure.
            log.error(
                    "Provider accepted transactional email but local completion could not be persisted messageId={} providerMessageId={}",
                    message.id(),
                    receipt.providerMessageId(),
                    exception
            );
            return;
        }

        if (!accepted) {
            return;
        }

        try {
            processingService.reconcileProviderDeliveryStatus(
                    message.id(),
                    receipt.providerMessageId()
            );
        } catch (RuntimeException exception) {
            // Provider acceptance is already durable. Delivery webhooks can still advance the state;
            // a reconciliation failure must never turn an accepted email into a provider failure.
            log.error(
                    "Transactional email accepted but webhook reconciliation failed messageId={} providerMessageId={}",
                    message.id(),
                    receipt.providerMessageId(),
                    exception
            );
        }
    }

    private String providerStatusCode(Integer status) {
        return status == null ? "EMAIL_DELIVERY_ERROR" : "HTTP_" + status;
    }
}
