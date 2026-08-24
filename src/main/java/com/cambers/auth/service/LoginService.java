package com.cambers.auth.service;

import com.cambers.auth.config.properties.SessionProperties;
import com.cambers.auth.dto.LoginRequest;
import com.cambers.auth.dto.TokenResponse;
import com.cambers.auth.entity.AuthSession;
import com.cambers.auth.entity.User;
import com.cambers.auth.exception.ProblemCode;
import com.cambers.auth.exception.UnauthorizedException;
import com.cambers.auth.ratelimit.LoginRateLimitService;
import com.cambers.auth.repository.AuthSessionRepository;
import com.cambers.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class LoginService {

    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenPairIssuer tokenPairIssuer;
    private final LoginRateLimitService loginRateLimitService;
    private final EmailNormalizer emailNormalizer;
    private final SessionProperties sessionProperties;
    private final Clock clock;
    private final String dummyPasswordHash;

    public LoginService(
            UserRepository userRepository,
            AuthSessionRepository authSessionRepository,
            PasswordEncoder passwordEncoder,
            TokenPairIssuer tokenPairIssuer,
            LoginRateLimitService loginRateLimitService,
            EmailNormalizer emailNormalizer,
            SessionProperties sessionProperties,
            Clock clock) {
        this.userRepository = userRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenPairIssuer = tokenPairIssuer;
        this.loginRateLimitService = loginRateLimitService;
        this.emailNormalizer = emailNormalizer;
        this.sessionProperties = sessionProperties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("authentication-service-dummy-password");
    }

    @Transactional
    public TokenResponse login(LoginRequest request, ClientMetadata clientMetadata) {
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

        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        Instant sessionExpiresAt = clock.instant().plus(sessionProperties.sessionTtl());
        AuthSession session = authSessionRepository.save(new AuthSession(
                user,
                sessionExpiresAt,
                clientMetadata.userAgent(),
                clientMetadata.ipAddress()
        ));

        return tokenPairIssuer.issue(user, session, null);
    }

    private UnauthorizedException invalidCredentials() {
        return new UnauthorizedException(
                ProblemCode.INVALID_CREDENTIALS,
                "The supplied credentials are invalid."
        );
    }
}
