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
            delivery.deliver(event.email(), event.rawToken(), event.expiresAt());
        } catch (RuntimeException exception) {
            // The account/token transaction is already committed. Resend remains available instead of returning a false 500.
            log.error("Could not deliver email verification message for {}", event.email(), exception);
        }
    }
}
