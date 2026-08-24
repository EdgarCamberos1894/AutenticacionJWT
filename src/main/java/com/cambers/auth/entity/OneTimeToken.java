package com.cambers.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "one_time_tokens")
public class OneTimeToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private TokenPurpose purpose;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true, updatable = false)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    protected OneTimeToken() {
    }

    public OneTimeToken(User user, TokenPurpose purpose, String tokenHash, Instant expiresAt) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.purpose = Objects.requireNonNull(purpose, "purpose must not be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public TokenPurpose getPurpose() {
        return purpose;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isInvalidated() {
        return invalidatedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isUsableAt(Instant now) {
        return !isConsumed() && !isInvalidated() && !isExpired(now);
    }

    public void consume(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (isConsumed() || isInvalidated()) {
            throw new IllegalStateException("Only an active one-time token can be consumed");
        }
        consumedAt = now;
    }
}
