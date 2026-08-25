package com.cambers.auth.account;

import java.util.Objects;
import java.util.UUID;

public record PasswordResetCompleted(UUID userId) {

    public PasswordResetCompleted {
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
