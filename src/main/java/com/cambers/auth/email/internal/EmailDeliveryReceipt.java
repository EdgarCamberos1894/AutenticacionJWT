package com.cambers.auth.email.internal;

public record EmailDeliveryReceipt(String providerMessageId) {
    public EmailDeliveryReceipt {
        if (providerMessageId == null || providerMessageId.isBlank()) {
            throw new IllegalArgumentException("providerMessageId must not be blank");
        }
    }
}
