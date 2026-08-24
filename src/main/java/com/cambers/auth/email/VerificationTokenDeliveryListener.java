package com.cambers.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class VerificationTokenDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(VerificationTokenDeliveryListener.class);

    private final VerificationTokenDelivery delivery;

    public VerificationTokenDeliveryListener(VerificationTokenDelivery delivery) {
        this.delivery = delivery;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerificationTokenIssued(VerificationTokenIssuedEvent event) {
        try {
            delivery.deliver(event.email(), event.rawToken(), event.expiresAt(), event.issuanceId());
        } catch (EmailDeliveryException exception) {
            log.error(
                    "Could not deliver email verification message issuanceId={} retryable={} providerStatus={} providerCode={}",
                    event.issuanceId(),
                    exception.isRetryable(),
                    exception.getProviderStatus(),
                    exception.getProviderCode(),
                    exception
            );
        } catch (RuntimeException exception) {
            log.error("Unexpected email verification delivery failure issuanceId={}", event.issuanceId(), exception);
        }
    }
}
