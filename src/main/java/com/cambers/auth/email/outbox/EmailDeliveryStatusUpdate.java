package com.cambers.auth.email.outbox;

import java.time.Instant;
import java.util.Objects;

public record EmailDeliveryStatusUpdate(
        EmailDeliveryStatus status,
        Instant occurredAt
) {
    public EmailDeliveryStatusUpdate {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
