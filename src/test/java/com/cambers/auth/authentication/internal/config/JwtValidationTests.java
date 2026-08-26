package com.cambers.auth.authentication.internal.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
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

class JwtValidationTests {

    private static final String ISSUER = "https://issuer.example";
    private static final String AUDIENCE = "authentication-api";

    private static KeyPair keyPair;

    private final JwtConfig jwtConfig = new JwtConfig();
    private final JwtProperties properties = new JwtProperties(
            ISSUER,
            AUDIENCE,
            Duration.ofMinutes(10),
            null
    );

    private JwtEncoder encoder;
    private JwtDecoder decoder;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        keyPair = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        JwtKeyRing keyRing = new JwtKeyRing(
                (RSAPublicKey) keyPair.getPublic(),
                (RSAPrivateKey) keyPair.getPrivate(),
                List.of()
        );
        encoder = jwtConfig.jwtEncoder(keyRing);
        decoder = jwtConfig.jwtDecoder(keyRing, properties);
    }

    @Test
    void acceptsTokenWithExpectedSecurityClaims() {
        String token = encode(validClaims());

        assertThat(decoder.decode(token).getClaimAsString("token_type")).isEqualTo("access");
    }

    @Test
    void rejectsWrongIssuer() {
        assertRejected(encode(validClaims("https://other-issuer.example", List.of(AUDIENCE), "access",
                UUID.randomUUID().toString(), UUID.randomUUID().toString())));
    }

    @Test
    void rejectsWrongAudience() {
        assertRejected(encode(validClaims(ISSUER, List.of("different-api"), "access",
                UUID.randomUUID().toString(), UUID.randomUUID().toString())));
    }

    @Test
    void rejectsNonAccessTokenType() {
        assertRejected(encode(validClaims(ISSUER, List.of(AUDIENCE), "refresh",
                UUID.randomUUID().toString(), UUID.randomUUID().toString())));
    }

    @Test
    void rejectsMalformedSubject() {
        assertRejected(encode(validClaims(ISSUER, List.of(AUDIENCE), "access",
                "not-a-uuid", UUID.randomUUID().toString())));
    }

    @Test
    void rejectsMalformedSessionId() {
        assertRejected(encode(validClaims(ISSUER, List.of(AUDIENCE), "access",
                UUID.randomUUID().toString(), "not-a-uuid")));
    }

    @Test
    void rejectsExpiredToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = claims(
                ISSUER,
                List.of(AUDIENCE),
                "access",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                now.minus(Duration.ofMinutes(10)),
                now.minus(Duration.ofMinutes(10)),
                now.minus(Duration.ofMinutes(5))
        );

        assertRejected(encode(claims));
    }

    @Test
    void rejectsTokenUsedBeforeNotBeforeInstant() {
        Instant now = Instant.now();
        JwtClaimsSet claims = claims(
                ISSUER,
                List.of(AUDIENCE),
                "access",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                now,
                now.plus(Duration.ofMinutes(5)),
                now.plus(Duration.ofMinutes(10))
        );

        assertRejected(encode(claims));
    }

    @Test
    void rejectsRsaAlgorithmOtherThanRs256() {
        JwtEncoder rs512Encoder = NimbusJwtEncoder.withKeyPair(
                (RSAPublicKey) keyPair.getPublic(),
                (RSAPrivateKey) keyPair.getPrivate()
        ).algorithm(SignatureAlgorithm.RS512).build();
        String token = rs512Encoder.encode(JwtEncoderParameters.from(validClaims())).getTokenValue();

        assertRejected(token);
    }

    private void assertRejected(String token) {
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private JwtClaimsSet validClaims() {
        return validClaims(
                ISSUER,
                List.of(AUDIENCE),
                "access",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString()
        );
    }

    private JwtClaimsSet validClaims(
            String issuer,
            List<String> audience,
            String tokenType,
            String subject,
            String sessionId
    ) {
        Instant now = Instant.now();
        return claims(
                issuer,
                audience,
                tokenType,
                subject,
                sessionId,
                now.minusSeconds(1),
                now.minusSeconds(1),
                now.plus(Duration.ofMinutes(10))
        );
    }

    private JwtClaimsSet claims(
            String issuer,
            List<String> audience,
            String tokenType,
            String subject,
            String sessionId,
            Instant issuedAt,
            Instant notBefore,
            Instant expiresAt
    ) {
        return JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .audience(audience)
                .issuedAt(issuedAt)
                .notBefore(notBefore)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId)
                .claim("roles", List.of("USER"))
                .claim("token_type", tokenType)
                .build();
    }
}
