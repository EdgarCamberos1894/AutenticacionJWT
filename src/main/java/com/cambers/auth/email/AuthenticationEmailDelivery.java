package com.cambers.auth.email;

import java.time.Instant;
import java.util.UUID;

/**
 * Public application-facing contract for durable authentication email delivery.
 * Provider, encryption, outbox and retry details remain internal to the delivery module.
 */
public interface AuthenticationEmailDelivery {

    void enqueueVerification(
            UUID issuanceId,
            String recipient,
            String rawToken,
            Instant expiresAt,
            Instant now
    );

    void enqueuePasswordReset(
            UUID issuanceId,
            String recipient,
            String rawToken,
            Instant expiresAt,
            Instant now
    );

    void cancelSuperseded(UUID issuanceId, Instant now);
}
