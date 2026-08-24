package com.cambers.auth.controller;

import com.cambers.auth.dto.LoginRequest;
import com.cambers.auth.dto.RefreshRequest;
import com.cambers.auth.dto.SessionResponse;
import com.cambers.auth.dto.TokenResponse;
import com.cambers.auth.service.AuthenticationService;
import com.cambers.auth.service.ClientMetadata;
import com.cambers.auth.service.SessionManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
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

    private final AuthenticationService authenticationService;
    private final SessionManagementService sessionManagementService;

    public AuthController(
            AuthenticationService authenticationService,
            SessionManagementService sessionManagementService) {
        this.authenticationService = authenticationService;
        this.sessionManagementService = sessionManagementService;
    }

    @PostMapping(path = "/login", consumes = "application/json", produces = "application/json")
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        ClientMetadata clientMetadata = new ClientMetadata(
                servletRequest.getHeader(HttpHeaders.USER_AGENT),
                servletRequest.getRemoteAddr()
        );
        return tokenResponse(authenticationService.login(request, clientMetadata));
    }

    @PostMapping(path = "/refresh", consumes = "application/json", produces = "application/json")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return tokenResponse(authenticationService.refresh(request));
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
    public List<SessionResponse> sessions(@AuthenticationPrincipal Jwt jwt) {
        return sessionManagementService.listActiveSessions(userId(jwt), sessionId(jwt));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId) {
        sessionManagementService.revokeSession(userId(jwt), sessionId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<TokenResponse> tokenResponse(TokenResponse response) {
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
