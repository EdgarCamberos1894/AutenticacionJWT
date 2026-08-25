package com.cambers.auth.authentication.internal.web;

import com.cambers.auth.authentication.AuthSessionResponse;
import com.cambers.auth.authentication.AuthenticationClientMetadata;
import com.cambers.auth.authentication.LoginRequest;
import com.cambers.auth.authentication.RefreshTokenRequest;
import com.cambers.auth.authentication.SessionAuthentication;
import com.cambers.auth.authentication.SessionManagement;
import com.cambers.auth.authentication.TokenPairResponse;
import com.cambers.auth.ratelimit.ClientIpResolver;
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
class AuthenticationController {

    private final SessionAuthentication sessionAuthentication;
    private final SessionManagement sessionManagement;
    private final ClientIpResolver clientIpResolver;

    AuthenticationController(
            SessionAuthentication sessionAuthentication,
            SessionManagement sessionManagement,
            ClientIpResolver clientIpResolver) {
        this.sessionAuthentication = sessionAuthentication;
        this.sessionManagement = sessionManagement;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping(path = "/login", consumes = "application/json", produces = "application/json")
    ResponseEntity<TokenPairResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        AuthenticationClientMetadata clientMetadata = new AuthenticationClientMetadata(
                servletRequest.getHeader(HttpHeaders.USER_AGENT),
                clientIpResolver.resolve(servletRequest)
        );
        return tokenResponse(sessionAuthentication.login(request, clientMetadata));
    }

    @PostMapping(path = "/refresh", consumes = "application/json", produces = "application/json")
    ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return tokenResponse(sessionAuthentication.refresh(request));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        sessionManagement.logoutCurrent(userId(jwt), sessionId(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    ResponseEntity<Void> logoutAll(@AuthenticationPrincipal Jwt jwt) {
        sessionManagement.logoutAll(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/sessions", produces = "application/json")
    List<AuthSessionResponse> listSessions(@AuthenticationPrincipal Jwt jwt) {
        return sessionManagement.listActiveSessions(userId(jwt), sessionId(jwt));
    }

    @DeleteMapping("/sessions/{sessionId}")
    ResponseEntity<Void> revokeSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId) {
        sessionManagement.revokeSession(userId(jwt), sessionId);
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
