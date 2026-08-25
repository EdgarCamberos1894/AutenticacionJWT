package com.cambers.auth.account;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Provider-neutral identity projection exposed to the authentication module.
 * Persistence entities and password hashes intentionally never cross this boundary.
 */
public record AccountPrincipal(UUID accountId, Set<String> roles) {

    public AccountPrincipal {
        Objects.requireNonNull(accountId, "accountId must not be null");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles must not be null"));
    }
}
