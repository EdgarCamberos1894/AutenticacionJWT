package com.cambers.auth.service;

import com.cambers.auth.config.properties.OneTimeTokenProperties;
import com.cambers.auth.email.VerificationTokenIssuedEvent;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class EmailVerificationService {

    private final OneTimeTokenRepository oneTimeTokenRepository;
    private final UserRepository userRepository;
    private final SecureOpaqueTokenGenerator tokenGenerator;
    private final OneTimeTokenProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public EmailVerificationService(
            OneTimeTokenRepository oneTimeTokenRepository,
            UserRepository userRepository,
            SecureOpaqueTokenGenerator tokenGenerator,
            OneTimeTokenProperties properties,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.oneTimeTokenRepository = oneTimeTokenRepository;
        this.userRepository = userRepository;
        this.tokenGenerator = tokenGenerator;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void issueVerification(User user) {
        if (user.isEmailVerified()) {
            return;
        }

        Instant now = clock.instant();
        oneTimeTokenRepository.consumeActiveTokens(user.getId(), TokenPurpose.VERIFY_EMAIL, now);

        GeneratedOpaqueToken generatedToken = tokenGenerator.generate();
        Instant expiresAt = now.plus(properties.emailVerificationTtl());
        oneTimeTokenRepository.save(new OneTimeToken(
                user,
                TokenPurpose.VERIFY_EMAIL,
                generatedToken.hash(),
                expiresAt
        ));

        eventPublisher.publishEvent(new VerificationTokenIssuedEvent(
                user.getEmail(),
                generatedToken.value(),
                expiresAt
        ));
    }

    @Transactional
    public void resend(String email) {
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(user -> !user.isEmailVerified())
                .filter(user -> user.getStatus() == AccountStatus.PENDING_VERIFICATION)
                .ifPresent(this::issueVerification);
    }

    @Transactional
    public void confirm(String rawToken) {
        String tokenHash = tokenGenerator.hash(rawToken);
        OneTimeToken token = oneTimeTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidVerificationToken);

        Instant now = clock.instant();
        if (token.getPurpose() != TokenPurpose.VERIFY_EMAIL || token.isConsumed() || token.isExpired(now)) {
            throw invalidVerificationToken();
        }

        User user = token.getUser();
        oneTimeTokenRepository.consumeActiveTokens(user.getId(), TokenPurpose.VERIFY_EMAIL, now);
        user.verifyEmail(now);
        if (user.getStatus() == AccountStatus.PENDING_VERIFICATION) {
            user.setStatus(AccountStatus.ACTIVE);
        }
    }

    private BadRequestException invalidVerificationToken() {
        return new BadRequestException(
                ProblemCode.INVALID_VERIFICATION_TOKEN,
                "The email verification token is invalid or expired."
        );
    }
}
