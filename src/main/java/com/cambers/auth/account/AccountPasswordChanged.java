package com.cambers.auth.account;

import java.util.Objects;
import java.util.UUID;

/**
 * Synchronous account-domain event emitted after a password credential changes.
 *
 * Consumers that protect credentials derived from the previous password state
 * should react inside the publishing transaction so a failed security reaction
 * can roll the password change back.
 */
public record AccountPasswordChanged(UUID accountId) {

    public AccountPasswordChanged {
        Objects.requireNonNull(accountId, "accountId must not be null");
    }
}
