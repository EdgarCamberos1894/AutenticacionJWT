package com.cambers.auth.email.outbox;

import com.cambers.auth.email.internal.TransactionalEmail;
import com.cambers.auth.email.internal.config.EmailOutboxProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Component
public class EmailOutboxCrypto {

    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final EmailOutboxProperties properties;
    private final JsonMapper jsonMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailOutboxCrypto(EmailOutboxProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        validateKey(properties.activeKeyId(), properties.activeKey());
        if (properties.hasPreviousKey()) {
            validateKey(properties.previousKeyId(), properties.previousKey());
        }
    }

    public EncryptedEmailPayload encrypt(UUID messageId, EmailOutboxPurpose purpose, TransactionalEmail email) {
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            byte[] plaintext = jsonMapper.writeValueAsBytes(email);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(properties.activeKey()), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad(messageId, purpose, properties.activeKeyId()));
            return new EncryptedEmailPayload(properties.activeKeyId(), nonce, cipher.doFinal(plaintext));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt email outbox payload", exception);
        }
    }

    public TransactionalEmail decrypt(ClaimedEmailOutboxMessage message) {
        return decrypt(
                message.id(),
                message.purpose(),
                message.keyId(),
                message.nonce(),
                message.ciphertext()
        );
    }

    public TransactionalEmail decrypt(EmailOutboxMessage message) {
        return decrypt(
                message.getId(),
                message.getPurpose(),
                message.getKeyId(),
                message.getNonce(),
                message.getCiphertext()
        );
    }

    private TransactionalEmail decrypt(
            UUID messageId,
            EmailOutboxPurpose purpose,
            String keyId,
            byte[] nonce,
            byte[] ciphertext) {
        String encodedKey = resolveKey(keyId);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(encodedKey), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad(messageId, purpose, keyId));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return jsonMapper.readValue(plaintext, TransactionalEmail.class);
        } catch (AEADBadTagException exception) {
            throw new IllegalStateException("Email outbox payload authentication failed", exception);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not decrypt email outbox payload", exception);
        }
    }

    private String resolveKey(String keyId) {
        if (properties.activeKeyId().equals(keyId)) {
            return properties.activeKey();
        }
        if (properties.hasPreviousKey() && properties.previousKeyId().equals(keyId)) {
            return properties.previousKey();
        }
        throw new IllegalStateException("No configured email outbox key for key id " + keyId);
    }

    private SecretKeySpec key(String encoded) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Email outbox encryption key must be Base64", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException("Email outbox encryption key must decode to exactly 32 bytes");
        }
        return new SecretKeySpec(decoded, "AES");
    }

    private void validateKey(String keyId, String encoded) {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("Email outbox key id must not be blank");
        }
        key(encoded);
    }

    private byte[] aad(UUID messageId, EmailOutboxPurpose purpose, String keyId) {
        return (messageId + ":" + purpose.name() + ":" + keyId).getBytes(StandardCharsets.UTF_8);
    }
}
