package com.cambers.auth.email.resend;

import com.cambers.auth.email.internal.config.ResendWebhookProperties;
import com.cambers.auth.platform.BadRequestException;
import com.cambers.auth.platform.ProblemCode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Component
public class ResendWebhookVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] signingKey;
    private final Duration tolerance;
    private final Clock clock;

    public ResendWebhookVerifier(ResendWebhookProperties properties, Clock clock) {
        this.signingKey = decodeSigningKey(properties.signingSecret());
        this.tolerance = properties.tolerance();
        this.clock = clock;
    }

    public VerifiedResendWebhook verify(byte[] rawBody, HttpHeaders headers) {
        String webhookId = header(headers, "svix-id", "webhook-id");
        String timestampValue = header(headers, "svix-timestamp", "webhook-timestamp");
        String signatures = header(headers, "svix-signature", "webhook-signature");

        if (webhookId == null || timestampValue == null || signatures == null) {
            throw invalidSignature();
        }

        Instant timestamp = parseTimestamp(timestampValue);
        Instant now = clock.instant();
        Duration skew = Duration.between(timestamp, now).abs();
        if (skew.compareTo(tolerance) > 0) {
            throw invalidSignature();
        }

        byte[] expected = expectedSignature(webhookId, timestampValue, rawBody);
        if (!matchesAnyV1Signature(expected, signatures)) {
            throw invalidSignature();
        }
        return new VerifiedResendWebhook(webhookId, timestamp);
    }

    private byte[] expectedSignature(String webhookId, String timestamp, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            mac.update(webhookId.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) '.');
            return mac.doFinal(rawBody);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not verify Resend webhook signature", exception);
        }
    }

    private boolean matchesAnyV1Signature(byte[] expected, String signatures) {
        for (String candidate : signatures.trim().split("\\s+")) {
            if (!candidate.startsWith("v1,")) {
                continue;
            }
            try {
                byte[] actual = Base64.getDecoder().decode(candidate.substring(3));
                if (MessageDigest.isEqual(expected, actual)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed candidates and continue checking any additional v1 signatures.
            }
        }
        return false;
    }

    private Instant parseTimestamp(String timestamp) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(timestamp));
        } catch (NumberFormatException | DateTimeException exception) {
            throw invalidSignature();
        }
    }

    private String header(HttpHeaders headers, String primary, String fallback) {
        String value = headers.getFirst(primary);
        return value != null ? value : headers.getFirst(fallback);
    }

    private byte[] decodeSigningKey(String signingSecret) {
        if (signingSecret == null || !signingSecret.startsWith("whsec_") || signingSecret.length() <= 6) {
            throw new IllegalArgumentException("Resend webhook signing secret must start with whsec_");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(signingSecret.substring(6));
            if (decoded.length < 16) {
                throw new IllegalArgumentException("Resend webhook signing secret is too short");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Resend webhook signing secret must contain valid Base64", exception);
        }
    }

    private BadRequestException invalidSignature() {
        return new BadRequestException(
                ProblemCode.INVALID_WEBHOOK_SIGNATURE,
                "The webhook signature is invalid."
        );
    }
}
