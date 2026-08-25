package com.cambers.auth.email.outbox;

public enum EmailOutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    DEAD
}
