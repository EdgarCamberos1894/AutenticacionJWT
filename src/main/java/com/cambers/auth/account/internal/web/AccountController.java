package com.cambers.auth.account.internal.web;

import com.cambers.auth.account.AccountRegistration;
import com.cambers.auth.account.EmailVerification;
import com.cambers.auth.account.PasswordRecovery;
import com.cambers.auth.dto.EmailVerificationRequest;
import com.cambers.auth.dto.EmailVerificationResendRequest;
import com.cambers.auth.dto.PasswordResetConfirmRequest;
import com.cambers.auth.dto.PasswordResetRequest;
import com.cambers.auth.dto.RegisterRequest;
import com.cambers.auth.dto.RegistrationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AccountController {

    private final AccountRegistration accountRegistration;
    private final EmailVerification emailVerification;
    private final PasswordRecovery passwordRecovery;

    AccountController(
            AccountRegistration accountRegistration,
            EmailVerification emailVerification,
            PasswordRecovery passwordRecovery) {
        this.accountRegistration = accountRegistration;
        this.emailVerification = emailVerification;
        this.passwordRecovery = passwordRecovery;
    }

    @PostMapping(path = "/register", consumes = "application/json", produces = "application/json")
    ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountRegistration.register(request));
    }

    @PostMapping(path = "/email-verification", consumes = "application/json")
    ResponseEntity<Void> resendEmailVerification(
            @Valid @RequestBody EmailVerificationResendRequest request) {
        emailVerification.resend(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping(path = "/email-verification/confirm", consumes = "application/json")
    ResponseEntity<Void> confirmEmailVerification(
            @Valid @RequestBody EmailVerificationRequest request) {
        emailVerification.confirm(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/password-reset", consumes = "application/json")
    ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordRecovery.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping(path = "/password-reset/confirm", consumes = "application/json")
    ResponseEntity<Void> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordRecovery.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
