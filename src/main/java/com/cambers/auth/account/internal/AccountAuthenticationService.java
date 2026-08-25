package com.cambers.auth.account.internal;

import com.cambers.auth.account.*;
import com.cambers.auth.account.internal.model.User;
import com.cambers.auth.account.internal.persistence.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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

    AccountAuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder, EmailNormalizer emailNormalizer, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailNormalizer = emailNormalizer;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("authentication-service-dummy-password");
    }

    @Override @Transactional
    public Optional<AuthenticatedAccount> authenticate(String email, String rawPassword) {
        User user = userRepository.findByEmailIgnoreCase(emailNormalizer.normalize(email)).orElse(null);
        String storedHash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(rawPassword, storedHash);
        if (user == null || !passwordMatches || !user.isAuthenticationAllowed()) return Optional.empty();
        if (requiresPasswordUpgrade(user.getPasswordHash())) {
            Instant now = clock.instant();
            user.changePasswordHash(passwordEncoder.encode(rawPassword), now);
        }
        return Optional.of(new AuthenticatedAccount(user.getId(), user.getRoles()));
    }

    @Override @Transactional(readOnly = true)
    public Optional<AuthenticatedAccount> findAuthenticationAllowed(UUID accountId) {
        return userRepository.findByIdWithRoles(accountId)
                .filter(User::isAuthenticationAllowed)
                .map(user -> new AuthenticatedAccount(user.getId(), user.getRoles()));
    }

    private boolean requiresPasswordUpgrade(String hash) {
        return hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$") || passwordEncoder.upgradeEncoding(hash);
    }
}
