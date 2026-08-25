package com.cambers.auth.email.outbox;

import java.time.Instant;
import java.util.UUID;

public record ClaimedEmailOutboxMessage(
        UUID id,
        EmailOutboxPurpose purpose,
        String keyId,
        byte[] nonce,
        byte[] ciphertext,
        int attemptCount,
        Instant expiresAt) {
}
