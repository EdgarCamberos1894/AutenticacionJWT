package com.cambers.auth.email.outbox;

public record EncryptedEmailPayload(String keyId, byte[] nonce, byte[] ciphertext) {
}
