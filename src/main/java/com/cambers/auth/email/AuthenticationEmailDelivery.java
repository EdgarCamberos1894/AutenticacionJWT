package com.cambers.auth.email;

import java.time.Instant;
import java.util.UUID;

public interface AuthenticationEmailDelivery {
    void enqueueVerification(UUID issuanceId, String recipient, String rawToken, Instant expiresAt, Instant now);
    void enqueuePasswordReset(UUID issuanceId, String recipient, String rawToken, Instant expiresAt, Instant now);
    void cancelSuperseded(UUID issuanceId, Instant now);
}
