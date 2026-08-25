package com.cambers.auth.account.internal.application;

import com.cambers.auth.account.AccountAuthentication;
import com.cambers.auth.account.AccountPrincipal;
import com.cambers.auth.entity.User;
import com.cambers.auth.repository.UserRepository;
import com.cambers.auth.service.EmailNormalizer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
class AccountAuthenticationService implements AccountAuthentication {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailNormalizer emailNormalizer;
    private final Clock clock;
    private final String dummyPasswordHash;

    AccountAuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailNormalizer emailNormalizer,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailNormalizer = emailNormalizer;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("authentication-service-dummy-password");
    }

    @Override
    public String normalizeLoginIdentifier(String email) {
        return emailNormalizer.normalize(email);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<AccountPrincipal> authenticate(String normalizedEmail, String rawPassword) {
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (user == null) {
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            return Optional.empty();
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash()) || !user.isAuthenticationAllowed()) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        if (requiresPasswordUpgrade(user.getPasswordHash())) {
            user.changePasswordHash(passwordEncoder.encode(rawPassword), now);
        }

        return Optional.of(toPrincipal(user));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<AccountPrincipal> findActive(UUID accountId) {
        return userRepository.findById(accountId)
                .filter(User::isAuthenticationAllowed)
                .map(this::toPrincipal);
    }

    private AccountPrincipal toPrincipal(User user) {
        return new AccountPrincipal(
                user.getId(),
                user.getRoles().stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet())
        );
    }

    private boolean requiresPasswordUpgrade(String passwordHash) {
        return isUnprefixedLegacyBcrypt(passwordHash) || passwordEncoder.upgradeEncoding(passwordHash);
    }

    private boolean isUnprefixedLegacyBcrypt(String passwordHash) {
        return passwordHash.startsWith("$2a$")
                || passwordHash.startsWith("$2b$")
                || passwordHash.startsWith("$2y$");
    }
}
