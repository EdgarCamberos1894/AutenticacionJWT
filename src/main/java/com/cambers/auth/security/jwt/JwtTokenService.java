package com.cambers.auth.security.jwt;

import com.cambers.auth.config.properties.JwtProperties;
import com.cambers.auth.entity.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issueAccessToken(User user, UUID sessionId) {
        if (user.getId() == null) {
            throw new IllegalArgumentException("A persisted user is required to issue an access token");
        }

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        List<String> roles = user.getRoles().stream()
                .map(Enum::name)
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .audience(List.of(properties.audience()))
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId.toString())
                .claim("roles", roles)
                .claim("token_type", "access")
                .build();

        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(claims));
        return new IssuedAccessToken(jwt.getTokenValue(), expiresAt);
    }
}
