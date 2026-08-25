package com.cambers.auth.account;

import java.util.Objects;
import java.util.UUID;

public record PasswordChangedEvent(UUID accountId) {
    public PasswordChangedEvent {
        Objects.requireNonNull(accountId, "accountId must not be null");
    }
}
