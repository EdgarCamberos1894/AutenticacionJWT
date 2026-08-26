package com.cambers.auth.authentication.internal.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    private static final int MINIMUM_RSA_BITS = 3072;

    @Bean
    @Profile({"local", "test"})
    JwtKeyRing ephemeralJwtKeyRing() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(MINIMUM_RSA_BITS);
            KeyPair keyPair = generator.generateKeyPair();
            return new JwtKeyRing(
                    (RSAPublicKey) keyPair.getPublic(),
                    (RSAPrivateKey) keyPair.getPrivate(),
                    List.of()
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("RSA key generation is not available", exception);
        }
    }

    @Bean
    @Profile("prod")
    JwtKeyRing productionJwtKeyRing(JwtProperties properties, ResourceLoader resourceLoader) {
        JwtProperties.Keys keys = properties.keys();
        if (keys == null || isBlank(keys.publicKeyLocation()) || isBlank(keys.privateKeyLocation())) {
            throw new IllegalStateException(
                    "Production JWT keys are required. Configure JWT_PUBLIC_KEY_LOCATION and JWT_PRIVATE_KEY_LOCATION."
            );
        }

        try {
            RSAPublicKey activePublicKey = readPublicKey(resourceLoader.getResource(keys.publicKeyLocation()));
            RSAPrivateKey activePrivateKey = readPrivateKey(resourceLoader.getResource(keys.privateKeyLocation()));
            validateKeyPair(activePublicKey, activePrivateKey);

            List<RSAPublicKey> previousPublicKeys = new ArrayList<>();
            for (String previousLocation : keys.previousPublicKeyLocations()) {
                RSAPublicKey previousPublicKey = readPublicKey(resourceLoader.getResource(previousLocation));
                validatePublicKey(previousPublicKey);
                previousPublicKeys.add(previousPublicKey);
            }

            return new JwtKeyRing(activePublicKey, activePrivateKey, previousPublicKeys);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("Unable to load production RSA JWT keys", exception);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(JwtKeyRing keyRing) {
        // Spring Security 7.1 derives a RFC 7638 thumbprint kid for this key pair.
        return NimbusJwtEncoder.withKeyPair(
                keyRing.activePublicKey(),
                keyRing.activePrivateKey()
        ).build();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtKeyRing keyRing, JwtProperties properties) {
        List<JWK> verificationKeys = keyRing.verificationPublicKeys().stream()
                .map(this::verificationJwk)
                .map(JWK.class::cast)
                .toList();

        ImmutableJWKSet<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(verificationKeys));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audience = new JwtAudienceValidator(properties.audience());
        OAuth2TokenValidator<Jwt> tokenType = new JwtClaimValidator<String>(
                "token_type",
                "access"::equals
        );
        OAuth2TokenValidator<Jwt> subject = new JwtClaimValidator<String>("sub", JwtConfig::isUuid);
        OAuth2TokenValidator<Jwt> sessionId = new JwtClaimValidator<String>("sid", JwtConfig::isUuid);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                defaults,
                audience,
                tokenType,
                subject,
                sessionId
        ));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private RSAKey verificationJwk(RSAPublicKey publicKey) {
        try {
            return new RSAKey.Builder(publicKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to derive JWT verification key id", exception);
        }
    }

    private RSAPublicKey readPublicKey(Resource resource)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] encoded = decodePem(resource, "PUBLIC KEY");
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(encoded));
    }

    private RSAPrivateKey readPrivateKey(Resource resource)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] encoded = decodePem(resource, "PRIVATE KEY");
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    private void validateKeyPair(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        if (!publicKey.getModulus().equals(privateKey.getModulus())) {
            throw new IllegalStateException("Configured JWT public and private keys do not belong to the same RSA key pair");
        }
        validatePublicKey(publicKey);
    }

    private void validatePublicKey(RSAPublicKey publicKey) {
        if (publicKey.getModulus().bitLength() < MINIMUM_RSA_BITS) {
            throw new IllegalStateException("Production JWT RSA keys must be at least " + MINIMUM_RSA_BITS + " bits");
        }
    }

    private byte[] decodePem(Resource resource, String type) throws IOException {
        String pem = resource.getContentAsString(StandardCharsets.US_ASCII)
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(pem);
    }

    private static boolean isUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
