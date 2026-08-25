package com.cambers.auth.account;

import java.util.Optional;
import java.util.UUID;

public interface AccountAuthentication {
    Optional<AuthenticatedAccount> authenticate(String email, String rawPassword);
    Optional<AuthenticatedAccount> findAuthenticationAllowed(UUID accountId);
}
