package com.cambers.auth.account.internal.password;

import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.authentication.password.CompromisedPasswordDecision;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * HIBP range-api checker that deliberately propagates provider failures.
 *
 * <p>Spring Security's stock HIBP checker treats REST failures as an empty result. Password
 * mutation in this service is security-sensitive, so an unavailable provider must be
 * distinguishable from a clean password and is mapped to 503 by the account policy.</p>
 */
public final class FailClosedHaveIBeenPwnedPasswordChecker implements CompromisedPasswordChecker {

    private static final int PREFIX_LENGTH = 5;

    private final RestClient restClient;

    public FailClosedHaveIBeenPwnedPasswordChecker(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
    }

    @Override
    public CompromisedPasswordDecision check(String password) {
        if (password == null) {
            return new CompromisedPasswordDecision(false);
        }

        String encodedHash = sha1Hex(password);
        String prefix = encodedHash.substring(0, PREFIX_LENGTH);
        String suffix = encodedHash.substring(PREFIX_LENGTH);

        String response = restClient.get()
                .uri(prefix)
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            throw new IllegalStateException("HIBP returned an empty password-range response");
        }

        boolean compromised = response.lines()
                .map(String::strip)
                .anyMatch(line -> matchesSuffix(line, suffix));
        return new CompromisedPasswordDecision(compromised);
    }

    private boolean matchesSuffix(String line, String suffix) {
        int separator = line.indexOf(':');
        if (separator != suffix.length()) {
            return false;
        }
        return line.regionMatches(true, 0, suffix, 0, suffix.length());
    }

    private String sha1Hex(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of()
                    .formatHex(digest.digest(password.getBytes(StandardCharsets.UTF_8)))
                    .toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is unavailable for HIBP range lookup", exception);
        }
    }
}
