package com.cambers.auth.email;

import java.time.Instant;
import java.util.UUID;

public interface VerificationTokenDelivery {

    void deliver(String email, String rawToken, Instant expiresAt, UUID issuanceId);
}
