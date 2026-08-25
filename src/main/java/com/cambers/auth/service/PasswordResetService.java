package com.cambers.auth.service;

import com.cambers.auth.config.properties.OneTimeTokenProperties;
import com.cambers.auth.email.outbox.EmailOutboxService;
import com.cambers.auth.entity.OneTimeToken;
import com.cambers.auth.entity.SessionRevocationReason;
import com.cambers.auth.entity.TokenPurpose;
import com.cambers.auth.entity.User;
import com.cambers.auth.exception.BadRequestException;
import com.cambers.auth.exception.ProblemCode;
import com.cambers.auth.repository.OneTimeTokenRepository;
import com.cambers.auth.repository.UserRepository;
import com.cambers.auth.security.token.GeneratedOpaqueToken;
import com.cambers.auth.security.token.SecureOpaqueTokenGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final OneTimeTokenRepository oneTimeTokenRepository;
    private final UserRepository userRepository;
    private final SecureOpaqueTokenGenerator tokenGenerator;
    private final OneTimeTokenProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final SessionRevocationService sessionRevocationService;
    private final EmailOutboxService emailOutboxService;
    private final EmailNormalizer emailNormalizer;
    private final Clock clock;

    public PasswordResetService(
            OneTimeTokenRepository oneTimeTokenRepository,
            UserRepository userRepository,
            SecureOpaqueTokenGenerator tokenGenerator,
            OneTimeTokenProperties properties,
            PasswordEncoder passwordEncoder,
            SessionRevocationService sessionRevocationService,
            EmailOutboxService emailOutboxService,
            EmailNormalizer emailNormalizer,
            Clock clock) {
        this.oneTimeTokenRepository = oneTimeTokenRepository;
        this.userRepository = userRepository;
        this.tokenGenerator = tokenGenerator;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.sessionRevocationService = sessionRevocationService;
        this.emailOutboxService = emailOutboxService;
        this.emailNormalizer = emailNormalizer;
        this.clock = clock;
    }

    @Transactional
    public void requestReset(String email) {
        String normalizedEmail = emailNormalizer.normalize(email);
        userRepository.findByEmailIgnoreCaseForUpdate(normalizedEmail)
                .filter(User::isAuthenticationAllowed)
                .ifPresent(this::issueResetTokenForLockedUser);
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        String tokenHash = tokenGenerator.hash(rawToken);
        UUID userId = oneTimeTokenRepository.findUserIdByTokenHash(tokenHash)
                .orElseThrow(this::invalidResetToken);

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(this::invalidResetToken);
        OneTimeToken token = oneTimeTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidResetToken);

        Instant now = clock.instant();
        if (!token.getUser().getId().equals(userId)
                || token.getPurpose() != TokenPurpose.RESET_PASSWORD
                || !token.isUsableAt(now)
                || !user.isAuthenticationAllowed()) {
            throw invalidResetToken();
        }

        token.consume(now);
        oneTimeTokenRepository.invalidateOtherActiveTokens(
                userId,
                TokenPurpose.RESET_PASSWORD,
                token.getId(),
                now
        );
        user.changePasswordHash(passwordEncoder.encode(newPassword), now);
        sessionRevocationService.revokeAllForUser(userId, SessionRevocationReason.PASSWORD_RESET);
    }

    private void issueResetTokenForLockedUser(User user) {
        Instant now = clock.instant();
        oneTimeTokenRepository.findActiveTokenId(user.getId(), TokenPurpose.RESET_PASSWORD)
                .ifPresent(issuanceId -> emailOutboxService.cancelSuperseded(issuanceId, now));
        oneTimeTokenRepository.invalidateActiveTokens(user.getId(), TokenPurpose.RESET_PASSWORD, now);

        GeneratedOpaqueToken generatedToken = tokenGenerator.generate();
        Instant expiresAt = now.plus(properties.passwordResetTtl());
        OneTimeToken token = oneTimeTokenRepository.save(new OneTimeToken(
                user,
                TokenPurpose.RESET_PASSWORD,
                generatedToken.hash(),
                now,
                expiresAt
        ));
        UUID issuanceId = Objects.requireNonNull(token.getId(), "Persisted password-reset token must have an id");

        emailOutboxService.enqueuePasswordReset(
                issuanceId,
                user.getEmail(),
                generatedToken.value(),
                expiresAt,
                now
        );
    }

    private BadRequestException invalidResetToken() {
        return new BadRequestException(
                ProblemCode.INVALID_PASSWORD_RESET_TOKEN,
                "The password reset token is invalid or expired."
        );
    }
}
