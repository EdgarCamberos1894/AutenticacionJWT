package com.cambers.auth.email.outbox;

public enum EmailDeliveryStatus {
    QUEUED(0),
    ACCEPTED(10),
    DELAYED(20),
    DELIVERED(30),
    FAILED(40),
    SUPPRESSED(40),
    BOUNCED(50),
    COMPLAINED(60);

    private final int precedence;

    EmailDeliveryStatus(int precedence) {
        this.precedence = precedence;
    }

    public int precedence() {
        return precedence;
    }
}
