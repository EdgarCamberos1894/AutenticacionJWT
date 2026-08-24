package com.cambers.auth.email;

import java.time.Instant;

public interface PasswordResetTokenDelivery {

    void deliver(String email, String rawToken, Instant expiresAt);
}
