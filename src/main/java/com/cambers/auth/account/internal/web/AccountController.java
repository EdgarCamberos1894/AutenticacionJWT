package com.cambers.auth.account.internal.web;

import com.cambers.auth.account.internal.application.EmailVerificationService;
import com.cambers.auth.account.internal.application.PasswordResetService;
import com.cambers.auth.account.internal.application.RegistrationService;
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

    private final RegistrationService registrationService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    AccountController(
            RegistrationService registrationService,
            EmailVerificationService emailVerificationService,
            PasswordResetService passwordResetService) {
        this.registrationService = registrationService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping(path = "/register", consumes = "application/json", produces = "application/json")
    ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.register(request));
    }

    @PostMapping(path = "/email-verification", consumes = "application/json")
    ResponseEntity<Void> resendEmailVerification(@Valid @RequestBody EmailVerificationResendRequest request) {
        emailVerificationService.resend(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping(path = "/email-verification/confirm", consumes = "application/json")
    ResponseEntity<Void> confirmEmailVerification(@Valid @RequestBody EmailVerificationRequest request) {
        emailVerificationService.confirm(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/password-reset", consumes = "application/json")
    ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping(path = "/password-reset/confirm", consumes = "application/json")
    ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
