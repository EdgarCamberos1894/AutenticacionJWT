package com.cambers.auth.email.outbox;

public enum EmailDeliveryStatus {
    QUEUED,
    ACCEPTED,
    DELIVERED,
    DELAYED,
    BOUNCED,
    COMPLAINED,
    FAILED,
    SUPPRESSED
}
