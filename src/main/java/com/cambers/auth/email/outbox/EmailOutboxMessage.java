package com.cambers.auth.email.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_outbox_messages")
public class EmailOutboxMessage {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EmailOutboxPurpose purpose;

    @Column(name = "key_id", nullable = false, length = 64)
    private String keyId;

    @Column(nullable = false)
    private byte[] nonce;

    @Column(nullable = false)
    private byte[] ciphertext;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EmailOutboxStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 24)
    private EmailDeliveryStatus deliveryStatus;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 128)
    private String lockedBy;

    @Column(name = "provider_message_id", length = 128)
    private String providerMessageId;

    @Column(name = "last_error_code", length = 128)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmailOutboxMessage() {
    }

    public EmailOutboxMessage(
            UUID id,
            EmailOutboxPurpose purpose,
            EncryptedEmailPayload payload,
            Instant now,
            Instant expiresAt) {
        this.id = id;
        this.purpose = purpose;
        this.keyId = payload.keyId();
        this.nonce = payload.nonce().clone();
        this.ciphertext = payload.ciphertext().clone();
        this.status = EmailOutboxStatus.PENDING;
        this.deliveryStatus = EmailDeliveryStatus.QUEUED;
        this.nextAttemptAt = now;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void claim(String workerId, Instant now) {
        this.status = EmailOutboxStatus.PROCESSING;
        this.lockedBy = workerId;
        this.lockedAt = now;
        this.attemptCount++;
        this.updatedAt = now;
    }

    public boolean isOwnedBy(String workerId) {
        return status == EmailOutboxStatus.PROCESSING && workerId.equals(lockedBy);
    }

    public void markAccepted(String providerMessageId, Instant now) {
        this.status = EmailOutboxStatus.SENT;
        this.deliveryStatus = EmailDeliveryStatus.ACCEPTED;
        this.providerMessageId = providerMessageId;
        this.sentAt = now;
        this.lastErrorCode = null;
        clearLease();
        this.updatedAt = now;
    }

    public void reschedule(String errorCode, Instant nextAttemptAt, Instant now) {
        this.status = EmailOutboxStatus.PENDING;
        this.lastErrorCode = errorCode;
        this.nextAttemptAt = nextAttemptAt;
        clearLease();
        this.updatedAt = now;
    }

    public void markDead(String errorCode, Instant now) {
        this.status = EmailOutboxStatus.DEAD;
        this.deliveryStatus = EmailDeliveryStatus.FAILED;
        this.lastErrorCode = errorCode;
        clearLease();
        this.updatedAt = now;
    }

    public void applyDeliveryStatus(EmailDeliveryStatus deliveryStatus, Instant now) {
        this.deliveryStatus = deliveryStatus;
        this.updatedAt = now;
    }

    private void clearLease() {
        this.lockedAt = null;
        this.lockedBy = null;
    }

    public UUID getId() { return id; }
    public EmailOutboxPurpose getPurpose() { return purpose; }
    public String getKeyId() { return keyId; }
    public byte[] getNonce() { return nonce.clone(); }
    public byte[] getCiphertext() { return ciphertext.clone(); }
    public EmailOutboxStatus getStatus() { return status; }
    public EmailDeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getProviderMessageId() { return providerMessageId; }
}
