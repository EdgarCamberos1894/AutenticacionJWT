package com.cambers.auth.authentication.internal;

import com.cambers.auth.account.EmailNormalizer;
import com.cambers.auth.authentication.AuthenticationClientMetadata;
import com.cambers.auth.config.properties.SessionProperties;
import com.cambers.auth.dto.LoginRequest;
import com.cambers.auth.dto.TokenPairResponse;
import com.cambers.auth.entity.AuthSession;
import com.cambers.auth.entity.User;
import com.cambers.auth.exception.ProblemCode;
import com.cambers.auth.exception.UnauthorizedException;
import com.cambers.auth.observability.SecurityAuditAction;
import com.cambers.auth.observability.SecurityAuditEvent;
import com.cambers.auth.observability.SecurityAuditOutcome;
import com.cambers.auth.observability.SecurityAuditPublisher;
import com.cambers.auth.observability.SecurityAuditReason;
import com.cambers.auth.ratelimit.LoginRateLimitService;
import com.cambers.auth.repository.AuthSessionRepository;
import com.cambers.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
class LoginService {

    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenPairIssuer tokenPairIssuer;
    private final LoginRateLimitService loginRateLimitService;
    private final EmailNormalizer emailNormalizer;
    private final SessionProperties sessionProperties;
    private final SecurityAuditPublisher auditPublisher;
    private final Clock clock;
    private final String dummyPasswordHash;

    LoginService(
            UserRepository userRepository,
            AuthSessionRepository authSessionRepository,
            PasswordEncoder passwordEncoder,
            TokenPairIssuer tokenPairIssuer,
            LoginRateLimitService loginRateLimitService,
            EmailNormalizer emailNormalizer,
            SessionProperties sessionProperties,
            SecurityAuditPublisher auditPublisher,
            Clock clock) {
        this.userRepository = userRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenPairIssuer = tokenPairIssuer;
        this.loginRateLimitService = loginRateLimitService;
        this.emailNormalizer = emailNormalizer;
        this.sessionProperties = sessionProperties;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("authentication-service-dummy-password");
    }

    @Transactional
    TokenPairResponse login(LoginRequest request, AuthenticationClientMetadata clientMetadata) {
        String normalizedEmail = emailNormalizer.normalize(request.email());
        loginRateLimitService.checkAccount(normalizedEmail);

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw invalidCredentials();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        if (!user.isAuthenticationAllowed()) {
            throw invalidCredentials();
        }

        Instant now = clock.instant();
        if (requiresPasswordUpgrade(user.getPasswordHash())) {
            user.changePasswordHash(passwordEncoder.encode(request.password()), now);
        }

        Instant sessionExpiresAt = now.plus(sessionProperties.sessionTtl());
        AuthSession session = authSessionRepository.save(new AuthSession(
                user,
                now,
                sessionExpiresAt,
                clientMetadata.userAgent(),
                clientMetadata.ipAddress()
        ));

        TokenPairResponse response = tokenPairIssuer.issue(user, session, null);
        auditPublisher.afterCommit(SecurityAuditEvent.of(
                SecurityAuditAction.LOGIN,
                SecurityAuditOutcome.SUCCESS,
                SecurityAuditReason.NONE,
                user.getId(),
                session.getId()
        ));
        return response;
    }

    private boolean requiresPasswordUpgrade(String passwordHash) {
        return isUnprefixedLegacyBcrypt(passwordHash) || passwordEncoder.upgradeEncoding(passwordHash);
    }

    private boolean isUnprefixedLegacyBcrypt(String passwordHash) {
        return passwordHash.startsWith("$2a$")
                || passwordHash.startsWith("$2b$")
                || passwordHash.startsWith("$2y$");
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
