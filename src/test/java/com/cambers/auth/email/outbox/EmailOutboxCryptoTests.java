package com.cambers.auth.email.outbox;

import com.cambers.auth.email.internal.EmailTag;
import com.cambers.auth.email.internal.TransactionalEmail;
import com.cambers.auth.email.internal.config.EmailOutboxProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailOutboxCryptoTests {

    private static final String KEY_ONE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String KEY_TWO = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";

    @Test
    void encryptsAndAuthenticatesTransactionalEmailPayload() {
        EmailOutboxCrypto crypto = crypto(properties("v1", KEY_ONE, null, null));
        UUID messageId = UUID.randomUUID();
        TransactionalEmail email = email(messageId);

        EncryptedEmailPayload encrypted = crypto.encrypt(messageId, EmailOutboxPurpose.EMAIL_VERIFICATION, email);
        ClaimedEmailOutboxMessage claimed = claim(messageId, encrypted);

        TransactionalEmail decrypted = crypto.decrypt(claimed);

        assertThat(decrypted).isEqualTo(email);
        assertThat(new String(encrypted.ciphertext())).doesNotContain("person@example.com", "secret-token");
    }

    @Test
    void rejectsTamperedCiphertext() {
        EmailOutboxCrypto crypto = crypto(properties("v1", KEY_ONE, null, null));
        UUID messageId = UUID.randomUUID();
        EncryptedEmailPayload encrypted = crypto.encrypt(
                messageId,
                EmailOutboxPurpose.EMAIL_VERIFICATION,
                email(messageId)
        );
        byte[] tampered = encrypted.ciphertext().clone();
        tampered[tampered.length - 1] ^= 1;

        ClaimedEmailOutboxMessage claimed = new ClaimedEmailOutboxMessage(
                messageId,
                EmailOutboxPurpose.EMAIL_VERIFICATION,
                encrypted.keyId(),
                encrypted.nonce(),
                tampered,
                1,
                Instant.now().plusSeconds(300)
        );

        assertThatThrownBy(() -> crypto.decrypt(claimed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authentication failed");
    }

    @Test
    void decryptsPayloadWrittenWithPreviousKeyDuringRotation() {
        UUID messageId = UUID.randomUUID();
        TransactionalEmail email = email(messageId);
        EmailOutboxCrypto oldCrypto = crypto(properties("v1", KEY_ONE, null, null));
        EncryptedEmailPayload encrypted = oldCrypto.encrypt(
                messageId,
                EmailOutboxPurpose.EMAIL_VERIFICATION,
                email
        );

        EmailOutboxCrypto rotatedCrypto = crypto(properties("v2", KEY_TWO, "v1", KEY_ONE));

        assertThat(rotatedCrypto.decrypt(claim(messageId, encrypted))).isEqualTo(email);
    }

    private EmailOutboxCrypto crypto(EmailOutboxProperties properties) {
        return new EmailOutboxCrypto(properties, JsonMapper.builder().build());
    }

    private EmailOutboxProperties properties(
            String activeKeyId,
            String activeKey,
            String previousKeyId,
            String previousKey) {
        return new EmailOutboxProperties(
                false,
                activeKeyId,
                activeKey,
                previousKeyId,
                previousKey,
                Duration.ofSeconds(2),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Duration.ofMinutes(5),
                20,
                8
        );
    }

    private ClaimedEmailOutboxMessage claim(UUID messageId, EncryptedEmailPayload payload) {
        return new ClaimedEmailOutboxMessage(
                messageId,
                EmailOutboxPurpose.EMAIL_VERIFICATION,
                payload.keyId(),
                payload.nonce(),
                payload.ciphertext(),
                1,
                Instant.now().plusSeconds(300)
        );
    }

    private TransactionalEmail email(UUID messageId) {
        return new TransactionalEmail(
                "person@example.com",
                "Verify email",
                "<p>secret-token</p>",
                "secret-token",
                "auth/email-verification/" + messageId,
                List.of(new EmailTag("category", "email_verification"))
        );
    }
}
