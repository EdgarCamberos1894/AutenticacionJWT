package com.cambers.auth.account;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountAuthentication {

    Optional<AuthenticatedAccount> authenticate(
            CanonicalEmail email,
            String rawPassword,
            Instant authenticatedAt);

    Optional<AuthenticatedAccount> findAuthenticatable(UUID accountId);
}
