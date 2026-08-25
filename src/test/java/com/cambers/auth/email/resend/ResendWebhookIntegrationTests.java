package com.cambers.auth.email.resend;

import com.cambers.auth.email.EmailTag;
import com.cambers.auth.email.TransactionalEmail;
import com.cambers.auth.email.outbox.EmailDeliveryStatus;
import com.cambers.auth.email.outbox.EmailOutboxCrypto;
import com.cambers.auth.email.outbox.EmailOutboxMessage;
import com.cambers.auth.email.outbox.EmailOutboxPurpose;
import com.cambers.auth.email.outbox.EmailOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ResendWebhookIntegrationTests {

    private static final byte[] SIGNING_KEY = new byte[32];
    private static final String PROVIDER_MESSAGE_ID = "email_webhook_test_123";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private EmailOutboxRepository outboxRepository;
    @Autowired private EmailOutboxCrypto outboxCrypto;
    @Autowired private ResendWebhookEventRepository eventRepository;

    @BeforeEach
    void cleanDatabase() {
        eventRepository.deleteAllInBatch();
        outboxRepository.deleteAllInBatch();
    }

    @Test
    void verifiedWebhookUpdatesDeliveryStateAndDuplicateIsIdempotent() throws Exception {
        EmailOutboxMessage message = acceptedOutboxMessage();
        Instant deliveredAt = Instant.now();
        String body = webhookBody("email.delivered", deliveredAt);
        String webhookId = "msg_delivered_123";

        performSignedWebhook(webhookId, body).andExpect(status().isNoContent());
        performSignedWebhook(webhookId, body).andExpect(status().isNoContent());

        EmailOutboxMessage updated = outboxRepository.findById(message.getId()).orElseThrow();
        assertThat(updated.getDeliveryStatus()).isEqualTo(EmailDeliveryStatus.DELIVERED);
        assertThat(eventRepository.count()).isEqualTo(1);

        String delayedBody = webhookBody("email.delivery_delayed", deliveredAt.plusSeconds(30));
        performSignedWebhook("msg_delayed_late", delayedBody).andExpect(status().isNoContent());
        assertThat(outboxRepository.findById(message.getId()).orElseThrow().getDeliveryStatus())
                .isEqualTo(EmailDeliveryStatus.DELIVERED);
    }

    @Test
    void invalidSignatureReturnsProblemDetailsWithoutPersistingEvent() throws Exception {
        String body = webhookBody("email.delivered", Instant.now());

        mockMvc.perform(post("/api/v1/webhooks/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("svix-id", "msg_invalid")
                        .header("svix-timestamp", Long.toString(Instant.now().getEpochSecond()))
                        .header("svix-signature", "v1,invalid")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));

        assertThat(eventRepository.count()).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions performSignedWebhook(
            String webhookId,
            String body) throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = sign(webhookId, timestamp, body.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(post("/api/v1/webhooks/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .header("svix-id", webhookId)
                .header("svix-timestamp", timestamp)
                .header("svix-signature", "v1," + signature)
                .content(body));
    }

    private EmailOutboxMessage acceptedOutboxMessage() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        TransactionalEmail email = new TransactionalEmail(
                "person@example.com",
                "Verify email",
                "<p>Verify</p>",
                "Verify",
                "auth/email-verification/" + id,
                List.of(new EmailTag("category", "email_verification"))
        );
        var payload = outboxCrypto.encrypt(id, EmailOutboxPurpose.EMAIL_VERIFICATION, email);
        EmailOutboxMessage message = new EmailOutboxMessage(
                id,
                EmailOutboxPurpose.EMAIL_VERIFICATION,
                payload,
                now,
                now.plusSeconds(600)
        );
        message.claim("test-worker", now);
        message.markAccepted(PROVIDER_MESSAGE_ID, now);
        return outboxRepository.saveAndFlush(message);
    }

    private String webhookBody(String type, Instant createdAt) {
        return """
                {
                  "type":"%s",
                  "created_at":"%s",
                  "data":{"email_id":"%s","to":["person@example.com"]}
                }
                """.formatted(type, createdAt, PROVIDER_MESSAGE_ID);
    }

    private String sign(String webhookId, String timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SIGNING_KEY, "HmacSHA256"));
        mac.update(webhookId.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
        mac.update((byte) '.');
        return Base64.getEncoder().encodeToString(mac.doFinal(body));
    }
}
