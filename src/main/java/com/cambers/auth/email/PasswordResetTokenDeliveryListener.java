package com.cambers.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordResetTokenDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenDeliveryListener.class);

    private final PasswordResetTokenDelivery delivery;

    public PasswordResetTokenDeliveryListener(PasswordResetTokenDelivery delivery) {
        this.delivery = delivery;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetTokenIssued(PasswordResetTokenIssuedEvent event) {
        try {
            delivery.deliver(event.email(), event.rawToken(), event.expiresAt());
        } catch (RuntimeException exception) {
            log.error("Could not deliver password reset message for {}", event.email(), exception);
        }
    }
}
