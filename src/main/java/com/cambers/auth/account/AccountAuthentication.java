package com.cambers.auth.account;

import java.util.Optional;
import java.util.UUID;

/**
 * Authentication-facing account API.
 *
 * The account module owns email canonicalization, password verification and
 * password-hash upgrades. Callers receive only an immutable identity projection.
 */
public interface AccountAuthentication {

    String normalizeLoginIdentifier(String email);

    Optional<AccountPrincipal> authenticate(String normalizedEmail, String rawPassword);

    Optional<AccountPrincipal> findActive(UUID accountId);
}
