package com.cambers.auth.service;

import com.cambers.auth.config.properties.OneTimeTokenProperties;
import com.cambers.auth.email.PasswordResetTokenIssuedEvent;
import com.cambers.auth.entity.OneTimeToken;
import com.cambers.auth.entity.User;
import com.cambers.auth.enums.AccountStatus;
import com.cambers.auth.enums.TokenPurpose;
import com.cambers.auth.exception.BadRequestException;
import com.cambers.auth.exception.ProblemCode;
import com.cambers.auth.repository.OneTimeTokenRepository;
import com.cambers.auth.repository.UserRepository;
import com.cambers.auth.security.token.GeneratedOpaqueToken;
import com.cambers.auth.security.token.SecureOpaqueTokenGenerator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class PasswordResetService {

    private final OneTimeTokenRepository oneTimeTokenRepository;
    private final UserRepository userRepository;
    private final SecureOpaqueTokenGenerator tokenGenerator;
    private final OneTimeTokenProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final SessionManagementService sessionManagementService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PasswordResetService(
            OneTimeTokenRepository oneTimeTokenRepository,
            UserRepository userRepository,
            SecureOpaqueTokenGenerator tokenGenerator,
            OneTimeTokenProperties properties,
            PasswordEncoder passwordEncoder,
            SessionManagementService sessionManagementService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.oneTimeTokenRepository = oneTimeTokenRepository;
        this.userRepository = userRepository;
        this.tokenGenerator = tokenGenerator;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.sessionManagementService = sessionManagementService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void requestReset(String email) {
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(User::isEmailVerified)
                .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
                .ifPresent(this::issueResetToken);
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        String tokenHash = tokenGenerator.hash(rawToken);
        OneTimeToken token = oneTimeTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidResetToken);

        Instant now = clock.instant();
        if (token.getPurpose() != TokenPurpose.RESET_PASSWORD || token.isConsumed() || token.isExpired(now)) {
            throw invalidResetToken();
        }

        User user = token.getUser();
        if (!user.isEmailVerified() || user.getStatus() != AccountStatus.ACTIVE) {
            throw invalidResetToken();
        }

        oneTimeTokenRepository.consumeActiveTokens(user.getId(), TokenPurpose.RESET_PASSWORD, now);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        sessionManagementService.revokeAllForPasswordReset(user.getId());
    }

    private void issueResetToken(User user) {
        Instant now = clock.instant();
        oneTimeTokenRepository.consumeActiveTokens(user.getId(), TokenPurpose.RESET_PASSWORD, now);

        GeneratedOpaqueToken generatedToken = tokenGenerator.generate();
        Instant expiresAt = now.plus(properties.passwordResetTtl());
        oneTimeTokenRepository.save(new OneTimeToken(
                user,
                TokenPurpose.RESET_PASSWORD,
                generatedToken.hash(),
                expiresAt
        ));

        eventPublisher.publishEvent(new PasswordResetTokenIssuedEvent(
                user.getEmail(),
                generatedToken.value(),
                expiresAt
        ));
    }

    private BadRequestException invalidResetToken() {
        return new BadRequestException(
                ProblemCode.INVALID_PASSWORD_RESET_TOKEN,
                "The password reset token is invalid or expired."
        );
    }
}
