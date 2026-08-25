package com.cambers.auth.account.internal;

import com.cambers.auth.account.AccountAuthentication;
import com.cambers.auth.account.AuthenticatedAccount;
import com.cambers.auth.account.CanonicalEmail;
import com.cambers.auth.entity.User;
import com.cambers.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
class AccountAuthenticationAdapter implements AccountAuthentication {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    AccountAuthenticationAdapter(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("authentication-service-dummy-password");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<AuthenticatedAccount> authenticate(
            CanonicalEmail email,
            String rawPassword,
            Instant authenticatedAt) {
        User user = userRepository.findByEmailIgnoreCase(email.value()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            return Optional.empty();
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash()) || !user.isAuthenticationAllowed()) {
            return Optional.empty();
        }

        if (requiresPasswordUpgrade(user.getPasswordHash())) {
            user.changePasswordHash(passwordEncoder.encode(rawPassword), authenticatedAt);
        }

        return Optional.of(toAuthenticatedAccount(user));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<AuthenticatedAccount> findAuthenticatable(UUID accountId) {
        return userRepository.findById(accountId)
                .filter(User::isAuthenticationAllowed)
                .map(this::toAuthenticatedAccount);
    }

    private AuthenticatedAccount toAuthenticatedAccount(User user) {
        return new AuthenticatedAccount(
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
