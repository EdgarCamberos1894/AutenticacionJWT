package com.cambers.auth.controller;

import com.cambers.auth.dto.LoginRequest;
import com.cambers.auth.dto.RefreshRequest;
import com.cambers.auth.dto.TokenResponse;
import com.cambers.auth.service.AuthenticationService;
import com.cambers.auth.service.ClientMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
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

    private ResponseEntity<TokenResponse> tokenResponse(TokenResponse response) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(response);
    }
}
