package com.cambers.auth.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 320, nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private AccountStatus status = AccountStatus.PENDING_VERIFICATION;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", length = 50, nullable = false)
    private Set<RoleName> roles = new HashSet<>();

    protected User() {
    }

    public User(String email, String passwordHash, Instant createdAt) {
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void changePasswordHash(String passwordHash, Instant changedAt) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public boolean isAuthenticationAllowed() {
        return status == AccountStatus.ACTIVE && isEmailVerified();
    }

    public void verifyEmail(Instant verifiedAt) {
        Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
        if (emailVerifiedAt != null) {
            return;
        }
        if (status != AccountStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("Only a pending account can be activated by email verification");
        }
        emailVerifiedAt = verifiedAt;
        status = AccountStatus.ACTIVE;
        updatedAt = verifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<RoleName> getRoles() {
        return Set.copyOf(roles);
    }

    public void assignRole(RoleName role, Instant changedAt) {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(changedAt, "changedAt must not be null");
        if (roles.add(role)) {
            updatedAt = changedAt;
        }
    }
}
