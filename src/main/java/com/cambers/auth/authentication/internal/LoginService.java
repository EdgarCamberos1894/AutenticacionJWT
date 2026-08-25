package com.cambers.auth.authentication.internal;

import com.cambers.auth.account.AccountAuthentication;
import com.cambers.auth.account.AuthenticatedAccount;
import com.cambers.auth.account.EmailNormalizer;
import com.cambers.auth.authentication.AuthenticationClientMetadata;
import com.cambers.auth.authentication.LoginRequest;
import com.cambers.auth.authentication.TokenPairResponse;
import com.cambers.auth.authentication.internal.model.AuthSession;
import com.cambers.auth.authentication.internal.persistence.AuthSessionRepository;
import com.cambers.auth.config.properties.SessionProperties;
import com.cambers.auth.exception.ProblemCode;
import com.cambers.auth.exception.UnauthorizedException;
import com.cambers.auth.observability.*;
import com.cambers.auth.ratelimit.LoginRateLimitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;

@Service
class LoginService {
    private final AccountAuthentication accountAuthentication;
    private final AuthSessionRepository authSessionRepository;
    private final TokenPairIssuer tokenPairIssuer;
    private final LoginRateLimitService loginRateLimitService;
    private final EmailNormalizer emailNormalizer;
    private final SessionProperties sessionProperties;
    private final SecurityAuditPublisher auditPublisher;
    private final Clock clock;

    LoginService(AccountAuthentication accountAuthentication, AuthSessionRepository authSessionRepository,
            TokenPairIssuer tokenPairIssuer, LoginRateLimitService loginRateLimitService, EmailNormalizer emailNormalizer,
            SessionProperties sessionProperties, SecurityAuditPublisher auditPublisher, Clock clock) {
        this.accountAuthentication = accountAuthentication;
        this.authSessionRepository = authSessionRepository;
        this.tokenPairIssuer = tokenPairIssuer;
        this.loginRateLimitService = loginRateLimitService;
        this.emailNormalizer = emailNormalizer;
        this.sessionProperties = sessionProperties;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Transactional
    TokenPairResponse login(LoginRequest request, AuthenticationClientMetadata clientMetadata) {
        String normalizedEmail = emailNormalizer.normalize(request.email());
        loginRateLimitService.checkAccount(normalizedEmail);
        AuthenticatedAccount account = accountAuthentication.authenticate(normalizedEmail, request.password())
                .orElseThrow(this::invalidCredentials);
        Instant now = clock.instant();
        AuthSession session = authSessionRepository.save(new AuthSession(account.id(), now,
                now.plus(sessionProperties.sessionTtl()), clientMetadata.userAgent(), clientMetadata.ipAddress()));
        TokenPairResponse response = tokenPairIssuer.issue(account, session, null);
        auditPublisher.afterCommit(SecurityAuditEvent.of(SecurityAuditAction.LOGIN, SecurityAuditOutcome.SUCCESS,
                SecurityAuditReason.NONE, account.id(), session.getId()));
        return response;
    }

    private UnauthorizedException invalidCredentials() {
        auditPublisher.now(SecurityAuditEvent.of(SecurityAuditAction.LOGIN, SecurityAuditOutcome.FAILURE,
                SecurityAuditReason.INVALID_CREDENTIALS, null, null));
        return new UnauthorizedException(ProblemCode.INVALID_CREDENTIALS, "The supplied credentials are invalid.");
    }
}
