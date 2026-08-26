package com.cambers.auth.account.internal.web;

import com.cambers.auth.account.AccountRegistration;
import com.cambers.auth.account.EmailVerification;
import com.cambers.auth.account.EmailVerificationRequest;
import com.cambers.auth.account.EmailVerificationResendRequest;
import com.cambers.auth.account.PasswordRecovery;
import com.cambers.auth.account.PasswordResetConfirmRequest;
import com.cambers.auth.account.PasswordResetRequest;
import com.cambers.auth.account.RegisterRequest;
import com.cambers.auth.account.RegistrationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Account lifecycle", description = "Registration, email verification and password recovery")
class AccountController {
    private final AccountRegistration accountRegistration;
    private final EmailVerification emailVerification;
    private final PasswordRecovery passwordRecovery;

    AccountController(AccountRegistration accountRegistration, EmailVerification emailVerification,
                      PasswordRecovery passwordRecovery) {
        this.accountRegistration = accountRegistration;
        this.emailVerification = emailVerification;
        this.passwordRecovery = passwordRecovery;
    }

    @Operation(summary = "Register a pending account")
    @ApiResponse(
            responseCode = "201",
            description = "Account registered and verification delivery queued",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = RegistrationResponse.class))
    )
    @PostMapping(path = "/register", consumes = "application/json", produces = "application/json")
    ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountRegistration.register(request));
    }

    @Operation(summary = "Request another email-verification message")
    @ApiResponse(responseCode = "202", description = "Request accepted")
    @PostMapping(path = "/email-verification", consumes = "application/json")
    ResponseEntity<Void> resendEmailVerification(@Valid @RequestBody EmailVerificationResendRequest request) {
        emailVerification.resend(request.email());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Confirm email verification")
    @ApiResponse(responseCode = "204", description = "Email verified")
    @PostMapping(path = "/email-verification/confirm", consumes = "application/json")
    ResponseEntity<Void> confirmEmailVerification(@Valid @RequestBody EmailVerificationRequest request) {
        emailVerification.confirm(request.token());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Request password recovery")
    @ApiResponse(responseCode = "202", description = "Request accepted")
    @PostMapping(path = "/password-reset", consumes = "application/json")
    ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordRecovery.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Confirm password reset")
    @ApiResponse(responseCode = "204", description = "Password replaced and existing sessions revoked")
    @PostMapping(path = "/password-reset/confirm", consumes = "application/json")
    ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordRecovery.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
