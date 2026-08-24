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
            delivery.deliver(event.email(), event.rawToken(), event.expiresAt(), event.issuanceId());
        } catch (EmailDeliveryException exception) {
            log.error(
                    "Could not deliver password reset message issuanceId={} retryable={} providerStatus={} providerCode={}",
                    event.issuanceId(),
                    exception.isRetryable(),
                    exception.getProviderStatus(),
                    exception.getProviderCode(),
                    exception
            );
        } catch (RuntimeException exception) {
            log.error("Unexpected password reset delivery failure issuanceId={}", event.issuanceId(), exception);
        }
    }
}
