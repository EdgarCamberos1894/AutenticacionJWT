package com.cambers.auth.authentication.internal.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyRotationTests {

    private final JwtConfig jwtConfig = new JwtConfig();
    private final JwtProperties properties = new JwtProperties(
            "https://issuer.example",
            "authentication-api",
            Duration.ofMinutes(10),
            null
    );

    @Test
    void activeEncoderEmitsKidAndDecoderAcceptsActiveToken() throws Exception {
        KeyPair active = rsaKeyPair();
        JwtKeyRing keyRing = keyRing(active, List.of());
        JwtEncoder encoder = jwtConfig.jwtEncoder(keyRing);
        JwtDecoder decoder = jwtConfig.jwtDecoder(keyRing, properties);

        Jwt encoded = encoder.encode(JwtEncoderParameters.from(validClaims()));

        assertThat(encoded.getHeaders().get("kid")).isInstanceOf(String.class);
        assertThat((String) encoded.getHeaders().get("kid")).isNotBlank();
        assertThat(decoder.decode(encoded.getTokenValue()).getSubject())
                .isEqualTo(encoded.getSubject());
    }

    @Test
    void tokenFromPreviousDeploymentRemainsValidDuringRotationWindow() throws Exception {
        KeyPair active = rsaKeyPair();
        KeyPair previous = rsaKeyPair();
        JwtKeyRing rotatingRing = keyRing(active, List.of((RSAPublicKey) previous.getPublic()));
        JwtEncoder previousDeploymentEncoder = NimbusJwtEncoder.withKeyPair(
                (RSAPublicKey) previous.getPublic(),
                (RSAPrivateKey) previous.getPrivate()
        ).build();
        Jwt oldToken = previousDeploymentEncoder.encode(JwtEncoderParameters.from(validClaims()));

        assertThat(oldToken.getHeaders().get("kid")).isNotNull();
        assertThat(jwtConfig.jwtDecoder(rotatingRing, properties).decode(oldToken.getTokenValue()).getSubject())
                .isEqualTo(oldToken.getSubject());
    }

    @Test
    void legacyTokenWithoutKidCanDrainDuringFirstRotation() throws Exception {
        KeyPair active = rsaKeyPair();
        KeyPair previous = rsaKeyPair();
        JwtKeyRing rotatingRing = keyRing(active, List.of((RSAPublicKey) previous.getPublic()));
        Jwt legacyToken = legacyEncoderWithoutKid(previous)
                .encode(JwtEncoderParameters.from(validClaims()));

        assertThat(legacyToken.getHeaders()).doesNotContainKey("kid");
        assertThat(jwtConfig.jwtDecoder(rotatingRing, properties).decode(legacyToken.getTokenValue()).getSubject())
                .isEqualTo(legacyToken.getSubject());
    }

    @Test
    void removingPreviousPublicKeyRejectsOldTokenAfterDrainWindow() throws Exception {
        KeyPair active = rsaKeyPair();
        KeyPair previous = rsaKeyPair();
        Jwt oldToken = NimbusJwtEncoder.withKeyPair(
                (RSAPublicKey) previous.getPublic(),
                (RSAPrivateKey) previous.getPrivate()
        ).build().encode(JwtEncoderParameters.from(validClaims()));

        JwtKeyRing drainedRing = keyRing(active, List.of());
        JwtDecoder decoder = jwtConfig.jwtDecoder(drainedRing, properties);

        assertThatThrownBy(() -> decoder.decode(oldToken.getTokenValue()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void duplicateVerificationKeysAreRejected() throws Exception {
        KeyPair active = rsaKeyPair();
        RSAPublicKey activePublicKey = (RSAPublicKey) active.getPublic();

        assertThatThrownBy(() -> new JwtKeyRing(
                activePublicKey,
                (RSAPrivateKey) active.getPrivate(),
                List.of(activePublicKey)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
    }

    private JwtEncoder legacyEncoderWithoutKid(KeyPair keyPair) throws Exception {
        RSAKey jwkWithoutKid = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwkWithoutKid)));
    }

    private JwtKeyRing keyRing(KeyPair active, List<RSAPublicKey> previous) {
        return new JwtKeyRing(
                (RSAPublicKey) active.getPublic(),
                (RSAPrivateKey) active.getPrivate(),
                previous
        );
    }

    private KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        return generator.generateKeyPair();
    }

    private JwtClaimsSet validClaims() {
        Instant now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(UUID.randomUUID().toString())
                .audience(List.of(properties.audience()))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("sid", UUID.randomUUID().toString())
                .claim("roles", List.of("USER"))
                .claim("token_type", "access")
                .build();
    }
}
