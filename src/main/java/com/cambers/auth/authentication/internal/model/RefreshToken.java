package com.cambers.auth.authentication.internal.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "session_id", nullable = false)
    private AuthSession session;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_token_id")
    private RefreshToken parentToken;
    @Column(name = "token_hash", length = 64, nullable = false, unique = true, updatable = false)
    private String tokenHash;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "used_at")
    private Instant usedAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshToken() {}
    public RefreshToken(AuthSession session, RefreshToken parentToken, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.parentToken = parentToken;
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
    public UUID getId() { return id; }
    public AuthSession getSession() { return session; }
    public boolean isUsed() { return usedAt != null; }
    public boolean isRevoked() { return revokedAt != null; }
    public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }
    public void markUsed(Instant now) { usedAt = Objects.requireNonNull(now, "now must not be null"); }
}
