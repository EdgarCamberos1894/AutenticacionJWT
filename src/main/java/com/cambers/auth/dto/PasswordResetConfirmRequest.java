package com.cambers.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank
        @Size(max = 512)
        String token,

        @NotNull
        @Size(min = 15, max = 128)
        String newPassword
) {
}
