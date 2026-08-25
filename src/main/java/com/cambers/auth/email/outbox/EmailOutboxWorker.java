package com.cambers.auth.email.outbox;

import com.cambers.auth.config.properties.EmailOutboxProperties;
import com.cambers.auth.email.EmailDeliveryException;
import com.cambers.auth.email.EmailDeliveryReceipt;
import com.cambers.auth.email.TransactionalEmail;
import com.cambers.auth.email.TransactionalEmailSender;
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
    private final EmailOutboxClaimService claimService;
    private final EmailOutboxCrypto crypto;
    private final TransactionalEmailSender sender;
    private final EmailOutboxProperties properties;

    public EmailOutboxWorker(
            EmailOutboxClaimService claimService,
            EmailOutboxCrypto crypto,
            TransactionalEmailSender sender,
            EmailOutboxProperties properties) {
        this.claimService = claimService;
        this.crypto = crypto;
        this.sender = sender;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${auth.email.outbox.poll-interval:PT2S}")
    public void drain() {
        for (int index = 0; index < properties.batchSize(); index++) {
            var claimed = claimService.claimNext(workerId);
            if (claimed.isEmpty()) {
                return;
            }
            deliver(claimed.get());
        }
    }

    private void deliver(ClaimedEmailOutboxMessage message) {
        try {
            TransactionalEmail email = crypto.decrypt(message);
            EmailDeliveryReceipt receipt = sender.send(email);
            claimService.markAccepted(message.id(), workerId, receipt.providerMessageId());
        } catch (EmailDeliveryException exception) {
            log.warn(
                    "Transactional email delivery failed messageId={} attempt={} retryable={} providerStatus={} providerCode={}",
                    message.id(),
                    message.attemptCount(),
                    exception.isRetryable(),
                    exception.getProviderStatus(),
                    exception.getProviderCode()
            );
            claimService.markFailure(
                    message.id(),
                    workerId,
                    exception.isRetryable(),
                    exception.getProviderCode() != null
                            ? exception.getProviderCode()
                            : providerStatusCode(exception.getProviderStatus())
            );
        } catch (RuntimeException exception) {
            log.error("Unexpected transactional email outbox failure messageId={}", message.id(), exception);
            claimService.markFailure(message.id(), workerId, false, "OUTBOX_PROCESSING_ERROR");
        }
    }

    private String providerStatusCode(Integer status) {
        return status == null ? "EMAIL_DELIVERY_ERROR" : "HTTP_" + status;
    }
}
