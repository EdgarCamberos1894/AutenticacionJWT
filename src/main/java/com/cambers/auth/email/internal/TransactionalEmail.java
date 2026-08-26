package com.cambers.auth.email.internal;

import java.util.List;
import java.util.Objects;

public record TransactionalEmail(
        String recipient,
        String subject,
        String html,
        String text,
        String idempotencyKey,
        List<EmailTag> tags
) {
    public TransactionalEmail {
        recipient = requireText(recipient, "recipient");
        subject = requireText(subject, "subject");
        html = requireText(html, "html");
        text = requireText(text, "text");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.length() > 256) {
            throw new IllegalArgumentException("idempotencyKey must not exceed 256 characters");
        }
        tags = List.copyOf(Objects.requireNonNull(tags, "tags must not be null"));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
