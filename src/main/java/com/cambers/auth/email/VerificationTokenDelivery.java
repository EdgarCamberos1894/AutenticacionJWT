package com.cambers.auth.email;

import java.time.Instant;

public interface VerificationTokenDelivery {

    void deliver(String email, String rawToken, Instant expiresAt);
}
