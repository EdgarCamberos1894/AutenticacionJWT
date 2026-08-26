package com.cambers.auth.email.resend;

import com.cambers.auth.email.internal.config.ResendWebhookProperties;
import com.cambers.auth.platform.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResendWebhookVerifierTests {

    private static final byte[] KEY = new byte[32];
    private static final String SECRET = "whsec_" + Base64.getEncoder().encodeToString(KEY);
    private static final Instant NOW = Instant.parse("2026-08-24T23:00:00Z");

    @Test
    void verifiesOfficialSvixReferenceVector() {
        String secret = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
        String id = "msg_p5jXN8AQM9LWM0D4loKWxJek";
        String timestamp = "1614265330";
        byte[] body = "{\"test\": 2432232314}".getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.set("webhook-id", id);
        headers.set("webhook-timestamp", timestamp);
        headers.set("webhook-signature", "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=");

        Instant referenceTime = Instant.ofEpochSecond(Long.parseLong(timestamp));
        ResendWebhookVerifier verifier = new ResendWebhookVerifier(
                new ResendWebhookProperties(secret, Duration.ofMinutes(5)),
                Clock.fixed(referenceTime, ZoneOffset.UTC)
        );

        VerifiedResendWebhook verified = verifier.verify(body, headers);

        assertThat(verified.webhookId()).isEqualTo(id);
        assertThat(verified.timestamp()).isEqualTo(referenceTime);
    }

    @Test
    void verifiesRawPayloadAndSvixHeaders() throws Exception {
        byte[] body = "{\"type\":\"email.delivered\"}".getBytes(StandardCharsets.UTF_8);
        String id = "msg_test_123";
        String timestamp = Long.toString(NOW.getEpochSecond());
        HttpHeaders headers = signedHeaders(id, timestamp, body);

        VerifiedResendWebhook verified = verifier().verify(body, headers);

        assertThat(verified.webhookId()).isEqualTo(id);
        assertThat(verified.timestamp()).isEqualTo(NOW);
    }

    @Test
    void rejectsTamperedPayload() throws Exception {
        byte[] original = "{\"type\":\"email.delivered\"}".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(NOW.getEpochSecond());
        HttpHeaders headers = signedHeaders("msg_test_123", timestamp, original);
        byte[] tampered = "{\"type\":\"email.bounced\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> verifier().verify(tampered, headers))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("webhook signature is invalid");
    }

    @Test
    void rejectsReplayOutsideTimestampTolerance() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String staleTimestamp = Long.toString(NOW.minus(Duration.ofMinutes(6)).getEpochSecond());
        HttpHeaders headers = signedHeaders("msg_stale", staleTimestamp, body);

        assertThatThrownBy(() -> verifier().verify(body, headers))
                .isInstanceOf(BadRequestException.class);
    }

    private ResendWebhookVerifier verifier() {
        return new ResendWebhookVerifier(
                new ResendWebhookProperties(SECRET, Duration.ofMinutes(5)),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private HttpHeaders signedHeaders(String id, String timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
        mac.update(id.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
        mac.update((byte) '.');
        String signature = Base64.getEncoder().encodeToString(mac.doFinal(body));

        HttpHeaders headers = new HttpHeaders();
        headers.set("svix-id", id);
        headers.set("svix-timestamp", timestamp);
        headers.set("svix-signature", "v1," + signature);
        return headers;
    }
}
