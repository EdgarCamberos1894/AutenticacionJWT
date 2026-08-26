package com.cambers.auth.authentication.internal.config;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * JWT signing and verification material for one controlled rotation window.
 *
 * <p>Only the active key has private material. Previous keys are verification-only and may
 * remain configured until every access token signed by them has expired.</p>
 */
public record JwtKeyRing(
        RSAPublicKey activePublicKey,
        RSAPrivateKey activePrivateKey,
        List<RSAPublicKey> previousPublicKeys
) {

    public JwtKeyRing {
        Objects.requireNonNull(activePublicKey, "activePublicKey must not be null");
        Objects.requireNonNull(activePrivateKey, "activePrivateKey must not be null");
        previousPublicKeys = previousPublicKeys == null ? List.of() : List.copyOf(previousPublicKeys);

        Set<java.math.BigInteger> moduli = new HashSet<>();
        moduli.add(activePublicKey.getModulus());
        for (RSAPublicKey previousPublicKey : previousPublicKeys) {
            Objects.requireNonNull(previousPublicKey, "previousPublicKeys must not contain null");
            if (!moduli.add(previousPublicKey.getModulus())) {
                throw new IllegalArgumentException("JWT verification keys must be unique");
            }
        }
    }

    public List<RSAPublicKey> verificationPublicKeys() {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(activePublicKey),
                        previousPublicKeys.stream()
                )
                .toList();
    }
}
