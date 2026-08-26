package com.cambers.auth.account.internal;

import com.cambers.auth.account.*;
import com.cambers.auth.account.internal.config.OneTimeTokenProperties;
import com.cambers.auth.account.internal.model.*;
import com.cambers.auth.account.internal.password.PasswordCompromisePolicy;
import com.cambers.auth.account.internal.persistence.*;
import com.cambers.auth.account.internal.token.GeneratedOpaqueToken;
import com.cambers.auth.account.internal.token.SecureOpaqueTokenGenerator;
import com.cambers.auth.email.AuthenticationEmailDelivery;
import com.cambers.auth.observability.*;
import com.cambers.auth.platform.BadRequestException;
import com.cambers.auth.platform.ProblemCode;
import com.cambers.auth.ratelimit.RecoveryRateLimitService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class PasswordResetService implements PasswordRecovery {
    private final OneTimeTokenRepository oneTimeTokenRepository;
    private final UserRepository userRepository;
    private final SecureOpaqueTokenGenerator tokenGenerator;
    private final OneTimeTokenProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final PasswordCompromisePolicy passwordCompromisePolicy;
    private final AuthenticationEmailDelivery emailDelivery;
    private final EmailNormalizer emailNormalizer;
    private final RecoveryRateLimitService recoveryRateLimitService;
    private final SecurityAuditPublisher auditPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PasswordResetService(OneTimeTokenRepository oneTimeTokenRepository, UserRepository userRepository,
            SecureOpaqueTokenGenerator tokenGenerator, OneTimeTokenProperties properties, PasswordEncoder passwordEncoder,
            PasswordCompromisePolicy passwordCompromisePolicy,
            AuthenticationEmailDelivery emailDelivery, EmailNormalizer emailNormalizer,
            RecoveryRateLimitService recoveryRateLimitService,
            SecurityAuditPublisher auditPublisher, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.oneTimeTokenRepository = oneTimeTokenRepository;
        this.userRepository = userRepository;
        this.tokenGenerator = tokenGenerator;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.passwordCompromisePolicy = passwordCompromisePolicy;
        this.emailDelivery = emailDelivery;
        this.emailNormalizer = emailNormalizer;
        this.recoveryRateLimitService = recoveryRateLimitService;
        this.auditPublisher = auditPublisher;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override @Transactional
    public void requestReset(String email) {
        String normalizedEmail = emailNormalizer.normalize(email);
        recoveryRateLimitService.checkPasswordReset(normalizedEmail);
        userRepository.findByEmailIgnoreCaseForUpdate(normalizedEmail)
                .filter(User::isAuthenticationAllowed)
                .ifPresent(this::issueResetTokenForLockedUser);
        auditPublisher.afterCommit(SecurityAuditEvent.of(SecurityAuditAction.PASSWORD_RESET_REQUEST,
                SecurityAuditOutcome.ACCEPTED, SecurityAuditReason.NONE, null, null));
    }

    @Override @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        String tokenHash = tokenGenerator.hash(rawToken);
        UUID userId = oneTimeTokenRepository.findUserIdByTokenHash(tokenHash).orElseThrow(this::invalidResetToken);
        passwordCompromisePolicy.requireSafe(newPassword);
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(this::invalidResetToken);
        OneTimeToken token = oneTimeTokenRepository.findByTokenHashForUpdate(tokenHash).orElseThrow(this::invalidResetToken);
        Instant now = clock.instant();
        if (!token.getUser().getId().equals(userId) || token.getPurpose() != TokenPurpose.RESET_PASSWORD
                || !token.isUsableAt(now) || !user.isAuthenticationAllowed()) throw invalidResetToken();
        token.consume(now);
        oneTimeTokenRepository.invalidateOtherActiveTokens(userId, TokenPurpose.RESET_PASSWORD, token.getId(), now);
        user.changePasswordHash(passwordEncoder.encode(newPassword), now);
        eventPublisher.publishEvent(new PasswordResetCompleted(userId));
        auditPublisher.afterCommit(SecurityAuditEvent.of(SecurityAuditAction.PASSWORD_RESET_CONFIRM,
                SecurityAuditOutcome.SUCCESS, SecurityAuditReason.PASSWORD_RESET, userId, null));
    }

    private void issueResetTokenForLockedUser(User user) {
        Instant now = clock.instant();
        oneTimeTokenRepository.findActiveTokenId(user.getId(), TokenPurpose.RESET_PASSWORD)
                .ifPresent(issuanceId -> emailDelivery.cancelSuperseded(issuanceId, now));
        oneTimeTokenRepository.invalidateActiveTokens(user.getId(), TokenPurpose.RESET_PASSWORD, now);
        GeneratedOpaqueToken generatedToken = tokenGenerator.generate();
        Instant expiresAt = now.plus(properties.passwordResetTtl());
        OneTimeToken token = oneTimeTokenRepository.save(new OneTimeToken(user, TokenPurpose.RESET_PASSWORD,
                generatedToken.hash(), now, expiresAt));
        UUID issuanceId = Objects.requireNonNull(token.getId(), "Persisted password-reset token must have an id");
        emailDelivery.enqueuePasswordReset(issuanceId, user.getEmail(), generatedToken.value(), expiresAt, now);
    }

    private BadRequestException invalidResetToken() {
        auditPublisher.now(SecurityAuditEvent.of(SecurityAuditAction.PASSWORD_RESET_CONFIRM,
                SecurityAuditOutcome.FAILURE, SecurityAuditReason.INVALID_PASSWORD_RESET_TOKEN, null, null));
        return new BadRequestException(ProblemCode.INVALID_PASSWORD_RESET_TOKEN,
                "The password reset token is invalid or expired.");
    }
}
