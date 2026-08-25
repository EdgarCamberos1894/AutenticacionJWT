package com.cambers.auth.service;

import com.cambers.auth.config.properties.OneTimeTokenProperties;
import com.cambers.auth.email.AuthenticationEmailDelivery;
import com.cambers.auth.entity.AccountStatus;
import com.cambers.auth.entity.OneTimeToken;
import com.cambers.auth.entity.TokenPurpose;
import com.cambers.auth.entity.User;
import com.cambers.auth.exception.BadRequestException;
import com.cambers.auth.exception.ProblemCode;
import com.cambers.auth.observability.SecurityAuditAction;
import com.cambers.auth.observability.SecurityAuditEvent;
import com.cambers.auth.observability.SecurityAuditOutcome;
import com.cambers.auth.observability.SecurityAuditPublisher;
import com.cambers.auth.observability.SecurityAuditReason;
import com.cambers.auth.repository.OneTimeTokenRepository;
import com.cambers.auth.repository.UserRepository;
import com.cambers.auth.security.token.GeneratedOpaqueToken;
import com.cambers.auth.security.token.SecureOpaqueTokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private final OneTimeTokenRepository oneTimeTokenRepository;
    private final UserRepository userRepository;
    private final SecureOpaqueTokenGenerator tokenGenerator;
    private final OneTimeTokenProperties properties;
    private final AuthenticationEmailDelivery emailDelivery;
    private final EmailNormalizer emailNormalizer;
    private final SecurityAuditPublisher auditPublisher;
    private final Clock clock;

    public EmailVerificationService(
            OneTimeTokenRepository oneTimeTokenRepository,
            UserRepository userRepository,
            SecureOpaqueTokenGenerator tokenGenerator,
            OneTimeTokenProperties properties,
            AuthenticationEmailDelivery emailDelivery,
            EmailNormalizer emailNormalizer,
            SecurityAuditPublisher auditPublisher,
            Clock clock) {
        this.oneTimeTokenRepository = oneTimeTokenRepository;
        this.userRepository = userRepository;
        this.tokenGenerator = tokenGenerator;
        this.properties = properties;
        this.emailDelivery = emailDelivery;
        this.emailNormalizer = emailNormalizer;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Transactional
    public void issueVerification(User user) {
        User lockedUser = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new IllegalStateException("Cannot issue verification for a missing user"));
        issueVerificationForLockedUser(lockedUser);
    }

    @Transactional
    public void resend(String email) {
        String normalizedEmail = emailNormalizer.normalize(email);
        userRepository.findByEmailIgnoreCaseForUpdate(normalizedEmail)
                .filter(user -> !user.isEmailVerified())
                .filter(user -> user.getStatus() == AccountStatus.PENDING_VERIFICATION)
                .ifPresent(this::issueVerificationForLockedUser);

        auditPublisher.afterCommit(SecurityAuditEvent.of(
                SecurityAuditAction.EMAIL_VERIFICATION_REQUEST,
                SecurityAuditOutcome.ACCEPTED,
                SecurityAuditReason.NONE,
                null,
                null
        ));
    }

    @Transactional
    public void confirm(String rawToken) {
        String tokenHash = tokenGenerator.hash(rawToken);
        UUID userId = oneTimeTokenRepository.findUserIdByTokenHash(tokenHash)
                .orElseThrow(this::invalidVerificationToken);

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(this::invalidVerificationToken);
        OneTimeToken token = oneTimeTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidVerificationToken);

        Instant now = clock.instant();
        if (!token.getUser().getId().equals(userId)
                || token.getPurpose() != TokenPurpose.VERIFY_EMAIL
                || !token.isUsableAt(now)
                || user.getStatus() != AccountStatus.PENDING_VERIFICATION) {
            throw invalidVerificationToken();
        }

        token.consume(now);
        oneTimeTokenRepository.invalidateOtherActiveTokens(
                userId,
                TokenPurpose.VERIFY_EMAIL,
                token.getId(),
                now
        );
        user.verifyEmail(now);
        auditPublisher.afterCommit(SecurityAuditEvent.of(
                SecurityAuditAction.EMAIL_VERIFICATION_CONFIRM,
                SecurityAuditOutcome.SUCCESS,
                SecurityAuditReason.NONE,
                userId,
                null
        ));
    }

    private void issueVerificationForLockedUser(User user) {
        if (user.isEmailVerified() || user.getStatus() != AccountStatus.PENDING_VERIFICATION) {
            return;
        }

        Instant now = clock.instant();
        oneTimeTokenRepository.findActiveTokenId(user.getId(), TokenPurpose.VERIFY_EMAIL)
                .ifPresent(issuanceId -> emailDelivery.cancelSuperseded(issuanceId, now));
        oneTimeTokenRepository.invalidateActiveTokens(user.getId(), TokenPurpose.VERIFY_EMAIL, now);

        GeneratedOpaqueToken generatedToken = tokenGenerator.generate();
        Instant expiresAt = now.plus(properties.emailVerificationTtl());
        OneTimeToken token = oneTimeTokenRepository.save(new OneTimeToken(
                user,
                TokenPurpose.VERIFY_EMAIL,
                generatedToken.hash(),
                now,
                expiresAt
        ));
        UUID issuanceId = Objects.requireNonNull(token.getId(), "Persisted verification token must have an id");

        emailDelivery.enqueueVerification(
                issuanceId,
                user.getEmail(),
                generatedToken.value(),
                expiresAt,
                now
        );
    }

    private BadRequestException invalidVerificationToken() {
        auditPublisher.now(SecurityAuditEvent.of(
                SecurityAuditAction.EMAIL_VERIFICATION_CONFIRM,
                SecurityAuditOutcome.FAILURE,
                SecurityAuditReason.INVALID_VERIFICATION_TOKEN,
                null,
                null
        ));
        return new BadRequestException(
                ProblemCode.INVALID_VERIFICATION_TOKEN,
                "The email verification token is invalid or expired."
        );
    }
}
