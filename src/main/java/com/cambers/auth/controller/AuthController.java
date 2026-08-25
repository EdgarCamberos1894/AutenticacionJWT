package com.cambers.auth.controller;

import com.cambers.auth.dto.AuthSessionResponse;
import com.cambers.auth.dto.EmailVerificationRequest;
import com.cambers.auth.dto.EmailVerificationResendRequest;
import com.cambers.auth.dto.LoginRequest;
import com.cambers.auth.dto.PasswordResetConfirmRequest;
import com.cambers.auth.dto.PasswordResetRequest;
import com.cambers.auth.dto.RefreshTokenRequest;
import com.cambers.auth.dto.RegisterRequest;
import com.cambers.auth.dto.RegistrationResponse;
import com.cambers.auth.dto.TokenPairResponse;
import com.cambers.auth.ratelimit.ClientIpResolver;
import com.cambers.auth.service.AuthenticationFacade;
import com.cambers.auth.service.EmailVerificationService;
import com.cambers.auth.service.PasswordResetService;
import com.cambers.auth.service.RegistrationService;
import com.cambers.auth.service.SessionClientMetadata;
import com.cambers.auth.service.SessionManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationFacade authenticationFacade;
    private final RegistrationService registrationService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final SessionManagementService sessionManagementService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(
            AuthenticationFacade authenticationFacade,
            RegistrationService registrationService,
            EmailVerificationService emailVerificationService,
            PasswordResetService passwordResetService,
            SessionManagementService sessionManagementService,
            ClientIpResolver clientIpResolver) {
        this.authenticationFacade = authenticationFacade;
        this.registrationService = registrationService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.sessionManagementService = sessionManagementService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping(path = "/register", consumes = "application/json", produces = "application/json")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.register(request));
    }

    @PostMapping(path = "/login", consumes = "application/json", produces = "application/json")
    public ResponseEntity<TokenPairResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        SessionClientMetadata clientMetadata = new SessionClientMetadata(
                servletRequest.getHeader(HttpHeaders.USER_AGENT),
                clientIpResolver.resolve(servletRequest)
        );
        return tokenResponse(authenticationFacade.login(request, clientMetadata));
    }

    @PostMapping(path = "/refresh", consumes = "application/json", produces = "application/json")
    public ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return tokenResponse(authenticationFacade.refresh(request));
    }

    @PostMapping(path = "/email-verification", consumes = "application/json")
    public ResponseEntity<Void> resendEmailVerification(
            @Valid @RequestBody EmailVerificationResendRequest request) {
        emailVerificationService.resend(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping(path = "/email-verification/confirm", consumes = "application/json")
    public ResponseEntity<Void> confirmEmailVerification(
            @Valid @RequestBody EmailVerificationRequest request) {
        emailVerificationService.confirm(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/password-reset", consumes = "application/json")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping(path = "/password-reset/confirm", consumes = "application/json")
    public ResponseEntity<Void> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        sessionManagementService.logoutCurrent(userId(jwt), sessionId(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal Jwt jwt) {
        sessionManagementService.logoutAll(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/sessions", produces = "application/json")
    public List<AuthSessionResponse> listSessions(@AuthenticationPrincipal Jwt jwt) {
        return sessionManagementService.listActiveSessions(userId(jwt), sessionId(jwt));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId) {
        sessionManagementService.revokeSession(userId(jwt), sessionId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<TokenPairResponse> tokenResponse(TokenPairResponse response) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(response);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private UUID sessionId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("sid"));
    }
}
