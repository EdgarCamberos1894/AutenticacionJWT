package com.cambers.auth.service;

import com.cambers.auth.dto.LoginRequest;
import com.cambers.auth.dto.RefreshTokenRequest;
import com.cambers.auth.dto.TokenPairResponse;
import com.cambers.auth.exception.ProblemCode;
import com.cambers.auth.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationFacade {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFacade.class);

    private final LoginService loginService;
    private final RefreshTokenRotationService refreshTokenRotationService;
    private final SessionRevocationService sessionRevocationService;

    public AuthenticationFacade(
            LoginService loginService,
            RefreshTokenRotationService refreshTokenRotationService,
            SessionRevocationService sessionRevocationService) {
        this.loginService = loginService;
        this.refreshTokenRotationService = refreshTokenRotationService;
        this.sessionRevocationService = sessionRevocationService;
    }

    public TokenPairResponse login(LoginRequest request, SessionClientMetadata clientMetadata) {
        return loginService.login(request, clientMetadata);
    }

    public TokenPairResponse refresh(RefreshTokenRequest request) {
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
