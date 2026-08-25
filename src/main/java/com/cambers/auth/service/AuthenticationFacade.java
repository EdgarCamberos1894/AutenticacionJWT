package com.cambers.auth.service;

import com.cambers.auth.dto.LoginRequest;
import com.cambers.auth.dto.RefreshTokenRequest;
import com.cambers.auth.dto.TokenPairResponse;
import com.cambers.auth.exception.ProblemCode;
import com.cambers.auth.exception.UnauthorizedException;
import com.cambers.auth.observability.SecurityAuditAction;
import com.cambers.auth.observability.SecurityAuditEvent;
import com.cambers.auth.observability.SecurityAuditOutcome;
import com.cambers.auth.observability.SecurityAuditPublisher;
import com.cambers.auth.observability.SecurityAuditReason;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationFacade {

    private final LoginService loginService;
    private final RefreshTokenRotationService refreshTokenRotationService;
    private final SessionRevocationService sessionRevocationService;
    private final SecurityAuditPublisher auditPublisher;

    public AuthenticationFacade(
            LoginService loginService,
            RefreshTokenRotationService refreshTokenRotationService,
            SessionRevocationService sessionRevocationService,
            SecurityAuditPublisher auditPublisher) {
        this.loginService = loginService;
        this.refreshTokenRotationService = refreshTokenRotationService;
        this.sessionRevocationService = sessionRevocationService;
        this.auditPublisher = auditPublisher;
    }

    public TokenPairResponse login(LoginRequest request, SessionClientMetadata clientMetadata) {
        return loginService.login(request, clientMetadata);
    }

    public TokenPairResponse refresh(RefreshTokenRequest request) {
        try {
            TokenPairResponse response = refreshTokenRotationService.rotate(request.refreshToken());
            auditPublisher.afterCommit(SecurityAuditEvent.of(
                    SecurityAuditAction.REFRESH,
                    SecurityAuditOutcome.SUCCESS,
                    SecurityAuditReason.NONE,
                    null,
                    response.sessionId()
            ));
            return response;
        } catch (RefreshTokenReuseDetectedException exception) {
            sessionRevocationService.revokeCompromisedSession(exception.sessionId());
            auditPublisher.afterCommit(SecurityAuditEvent.of(
                    SecurityAuditAction.REFRESH,
                    SecurityAuditOutcome.DETECTED,
                    SecurityAuditReason.REFRESH_TOKEN_REUSE,
                    null,
                    exception.sessionId()
            ));
            throw new UnauthorizedException(
                    ProblemCode.INVALID_REFRESH_TOKEN,
                    "The refresh token is invalid or expired."
            );
        } catch (UnauthorizedException exception) {
            auditPublisher.now(SecurityAuditEvent.of(
                    SecurityAuditAction.REFRESH,
                    SecurityAuditOutcome.FAILURE,
                    SecurityAuditReason.INVALID_REFRESH_TOKEN,
                    null,
                    null
            ));
            throw exception;
        }
    }
}
