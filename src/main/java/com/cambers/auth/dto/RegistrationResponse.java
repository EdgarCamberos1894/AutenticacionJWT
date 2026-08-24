package com.cambers.auth.dto;

import java.util.UUID;

public record RegistrationResponse(
        UUID userId,
        String email,
        boolean emailVerificationRequired
) {
}
