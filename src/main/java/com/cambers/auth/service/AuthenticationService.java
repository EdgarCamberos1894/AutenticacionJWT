package com.cambers.auth.service;

import com.cambers.auth.dto.LoginRequest;
import com.cambers.auth.dto.RefreshRequest;
import com.cambers.auth.dto.TokenResponse;
import com.cambers.auth.exception.ProblemCode;
import com.cambers.auth.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final LoginService loginService;
    private final RefreshTokenRotationService refreshTokenRotationService;
    private final SessionRevocationService sessionRevocationService;

    public AuthenticationService(
            LoginService loginService,
            RefreshTokenRotationService refreshTokenRotationService,
            SessionRevocationService sessionRevocationService) {
        this.loginService = loginService;
        this.refreshTokenRotationService = refreshTokenRotationService;
        this.sessionRevocationService = sessionRevocationService;
    }

    public TokenResponse login(LoginRequest request, ClientMetadata clientMetadata) {
        return loginService.login(request, clientMetadata);
    }

    public TokenResponse refresh(RefreshRequest request) {
        try {
            return refreshTokenRotationService.rotate(request.refreshToken());
        } catch (RefreshTokenReuseDetectedException exception) {
            log.warn("Refresh token reuse detected for session {}", exception.sessionId());
            sessionRevocationService.revokeCompromisedSession(exception.sessionId());
            throw new UnauthorizedException(
                    ProblemCode.INVALID_REFRESH_TOKEN,
                    "The refresh token is invalid or expired."
            );
        }
    }
}
