package com.cambers.auth.account.internal;

import com.cambers.auth.account.AccountRegistration;
import com.cambers.auth.dto.RegisterRequest;
import com.cambers.auth.dto.RegistrationResponse;
import com.cambers.auth.entity.RoleName;
import com.cambers.auth.entity.User;
import com.cambers.auth.exception.EmailAlreadyRegisteredException;
import com.cambers.auth.observability.SecurityAuditAction;
import com.cambers.auth.observability.SecurityAuditEvent;
import com.cambers.auth.observability.SecurityAuditOutcome;
import com.cambers.auth.observability.SecurityAuditPublisher;
import com.cambers.auth.observability.SecurityAuditReason;
import com.cambers.auth.repository.UserRepository;
import com.cambers.auth.service.EmailNormalizer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class RegistrationService implements AccountRegistration {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final EmailNormalizer emailNormalizer;
    private final SecurityAuditPublisher auditPublisher;
    private final Clock clock;

    public RegistrationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService,
            EmailNormalizer emailNormalizer,
            SecurityAuditPublisher auditPublisher,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.emailNormalizer = emailNormalizer;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        String normalizedEmail = emailNormalizer.normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw alreadyRegistered();
        }

        Instant now = clock.instant();
        User user = new User(normalizedEmail, passwordEncoder.encode(request.password()), now);
        user.assignRole(RoleName.USER, now);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw alreadyRegistered();
        }

        emailVerificationService.issueVerification(user);
        auditPublisher.afterCommit(SecurityAuditEvent.of(
                SecurityAuditAction.REGISTRATION,
                SecurityAuditOutcome.SUCCESS,
                SecurityAuditReason.NONE,
                user.getId(),
                null
        ));
        return new RegistrationResponse(user.getId(), user.getEmail(), true);
    }

    private EmailAlreadyRegisteredException alreadyRegistered() {
        auditPublisher.now(SecurityAuditEvent.of(
                SecurityAuditAction.REGISTRATION,
                SecurityAuditOutcome.FAILURE,
                SecurityAuditReason.ACCOUNT_ALREADY_EXISTS,
                null,
                null
        ));
        return new EmailAlreadyRegisteredException();
    }
}
