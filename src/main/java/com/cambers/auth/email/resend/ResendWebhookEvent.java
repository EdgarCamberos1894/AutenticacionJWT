package com.cambers.auth.email.resend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "resend_webhook_events")
public class ResendWebhookEvent {

    @Id
    @Column(name = "webhook_id", length = 128)
    private String webhookId;

    @Column(name = "provider_message_id", length = 128)
    private String providerMessageId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "event_created_at")
    private Instant eventCreatedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected ResendWebhookEvent() {
    }

    public String getWebhookId() {
        return webhookId;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getEventCreatedAt() {
        return eventCreatedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
