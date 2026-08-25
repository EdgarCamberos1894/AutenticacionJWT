package com.cambers.auth.security.jwt;

import com.cambers.auth.account.AuthenticatedAccount;
import com.cambers.auth.config.properties.JwtProperties;
import org.springframework.security.oauth2.jwt.*;
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
        this.jwtEncoder = jwtEncoder; this.properties = properties; this.clock = clock;
    }
    public IssuedAccessToken issueAccessToken(AuthenticatedAccount account, UUID sessionId) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        List<String> roles = account.roles().stream().map(Enum::name).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.issuer()).subject(account.id().toString())
                .audience(List.of(properties.audience())).issuedAt(issuedAt).notBefore(issuedAt).expiresAt(expiresAt)
                .id(UUID.randomUUID().toString()).claim("sid", sessionId.toString()).claim("roles", roles)
                .claim("token_type", "access").build();
        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(claims));
        return new IssuedAccessToken(jwt.getTokenValue(), expiresAt);
    }
}
