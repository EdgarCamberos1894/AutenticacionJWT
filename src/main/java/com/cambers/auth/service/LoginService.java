package com.cambers.auth.service;

import com.cambers.auth.abuse.LoginRateLimitService;
import com.cambers.auth.account.AccountAuthentication;
import com.cambers.auth.account.AccountPrincipal;
import com.cambers.auth.config.properties.SessionProperties;
import com.cambers.auth.dto.LoginRequest;
import com.cambers.auth.dto.TokenPairResponse;
import com.cambers.auth.entity.AuthSession;
import com.cambers.auth.exception.ProblemCode;
import com.cambers.auth.exception.UnauthorizedException;
import com.cambers.auth.observability.SecurityAuditAction;
import com.cambers.auth.observability.SecurityAuditEvent;
import com.cambers.auth.observability.SecurityAuditOutcome;
import com.cambers.auth.observability.SecurityAuditPublisher;
import com.cambers.auth.observability.SecurityAuditReason;
import com.cambers.auth.repository.AuthSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class LoginService {

    private final AccountAuthentication accountAuthentication;
    private final AuthSessionRepository authSessionRepository;
    private final TokenPairIssuer tokenPairIssuer;
    private final LoginRateLimitService loginRateLimitService;
    private final SessionProperties sessionProperties;
    private final SecurityAuditPublisher auditPublisher;
    private final Clock clock;

    public LoginService(
            AccountAuthentication accountAuthentication,
            AuthSessionRepository authSessionRepository,
            TokenPairIssuer tokenPairIssuer,
            LoginRateLimitService loginRateLimitService,
            SessionProperties sessionProperties,
            SecurityAuditPublisher auditPublisher,
            Clock clock) {
        this.accountAuthentication = accountAuthentication;
        this.authSessionRepository = authSessionRepository;
        this.tokenPairIssuer = tokenPairIssuer;
        this.loginRateLimitService = loginRateLimitService;
        this.sessionProperties = sessionProperties;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Transactional
    public TokenPairResponse login(LoginRequest request, SessionClientMetadata clientMetadata) {
        String normalizedEmail = accountAuthentication.normalizeLoginIdentifier(request.email());
        loginRateLimitService.checkAccount(normalizedEmail);

        AccountPrincipal account = accountAuthentication.authenticate(normalizedEmail, request.password())
                .orElseThrow(this::invalidCredentials);

        Instant now = clock.instant();
        Instant sessionExpiresAt = now.plus(sessionProperties.sessionTtl());
        AuthSession session = authSessionRepository.save(new AuthSession(
                account.accountId(),
                now,
                sessionExpiresAt,
                clientMetadata.userAgent(),
                clientMetadata.ipAddress()
        ));

        TokenPairResponse response = tokenPairIssuer.issue(account, session, null);
        auditPublisher.afterCommit(SecurityAuditEvent.of(
                SecurityAuditAction.LOGIN,
                SecurityAuditOutcome.SUCCESS,
                SecurityAuditReason.NONE,
                account.accountId(),
                session.getId()
        ));
        return response;
    }

    private UnauthorizedException invalidCredentials() {
        auditPublisher.now(SecurityAuditEvent.of(
                SecurityAuditAction.LOGIN,
                SecurityAuditOutcome.FAILURE,
                SecurityAuditReason.INVALID_CREDENTIALS,
                null,
                null
        ));
        return new UnauthorizedException(
                ProblemCode.INVALID_CREDENTIALS,
                "The supplied credentials are invalid."
        );
    }
}
