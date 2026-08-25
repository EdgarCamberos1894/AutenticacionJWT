package com.cambers.auth.account;

import java.util.UUID;

public record RegistrationResponse(
        UUID userId,
        String email,
        boolean emailVerificationRequired
) {
}
