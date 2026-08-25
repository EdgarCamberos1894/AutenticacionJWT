package com.cambers.auth.email.resend;

import java.time.Instant;

public record VerifiedResendWebhook(String webhookId, Instant timestamp) {
}
