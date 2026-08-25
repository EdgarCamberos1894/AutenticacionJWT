package com.cambers.auth.account;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedAccount(UUID id, Set<RoleName> roles) {
    public AuthenticatedAccount {
        Objects.requireNonNull(id, "id must not be null");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles must not be null"));
    }
}
