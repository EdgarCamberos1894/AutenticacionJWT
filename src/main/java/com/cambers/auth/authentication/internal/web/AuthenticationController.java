package com.cambers.auth.authentication.internal.web;

import com.cambers.auth.authentication.AuthSessionResponse;
import com.cambers.auth.authentication.AuthenticationClientMetadata;
import com.cambers.auth.authentication.LoginRequest;
import com.cambers.auth.authentication.RefreshTokenRequest;
import com.cambers.auth.authentication.SessionAuthentication;
import com.cambers.auth.authentication.SessionManagement;
import com.cambers.auth.authentication.TokenPairResponse;
import com.cambers.auth.ratelimit.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
@Tag(name = "Authentication and sessions", description = "Login, refresh and authenticated session management")
class AuthenticationController {

    private static final String BEARER_AUTH = "bearerAuth";

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

    @Operation(summary = "Authenticate and create a session")
    @ApiResponse(
            responseCode = "200",
            description = "Bearer access token, rotating refresh token and session metadata",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = TokenPairResponse.class))
    )
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

    @Operation(summary = "Rotate a refresh token")
    @ApiResponse(
            responseCode = "200",
            description = "Rotated refresh token and a new access token",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = TokenPairResponse.class))
    )
    @PostMapping(path = "/refresh", consumes = "application/json", produces = "application/json")
    ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return tokenResponse(sessionAuthentication.refresh(request));
    }

    @Operation(summary = "Revoke the current session")
    @SecurityRequirement(name = BEARER_AUTH)
    @ApiResponse(responseCode = "204", description = "Current session revoked")
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        sessionManagement.logoutCurrent(userId(jwt), sessionId(jwt));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Revoke every session for the current account")
    @SecurityRequirement(name = BEARER_AUTH)
    @ApiResponse(responseCode = "204", description = "All sessions revoked")
    @PostMapping("/logout-all")
    ResponseEntity<Void> logoutAll(@AuthenticationPrincipal Jwt jwt) {
        sessionManagement.logoutAll(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List active sessions")
    @SecurityRequirement(name = BEARER_AUTH)
    @ApiResponse(
            responseCode = "200",
            description = "Active sessions for the authenticated account",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = AuthSessionResponse.class)))
    )
    @GetMapping(path = "/sessions", produces = "application/json")
    List<AuthSessionResponse> listSessions(@AuthenticationPrincipal Jwt jwt) {
        return sessionManagement.listActiveSessions(userId(jwt), sessionId(jwt));
    }

    @Operation(summary = "Revoke one owned session")
    @SecurityRequirement(name = BEARER_AUTH)
    @ApiResponse(responseCode = "204", description = "Session revoked")
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
