package com.cambers.auth.service;

import com.cambers.auth.config.properties.SessionProperties;
import com.cambers.auth.dto.TokenResponse;
import com.cambers.auth.entity.AuthSession;
import com.cambers.auth.entity.RefreshToken;
import com.cambers.auth.entity.User;
import com.cambers.auth.repository.RefreshTokenRepository;
import com.cambers.auth.security.jwt.IssuedAccessToken;
import com.cambers.auth.security.jwt.JwtTokenService;
import com.cambers.auth.security.refresh.GeneratedRefreshToken;
import com.cambers.auth.security.refresh.RefreshTokenGenerator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
public class TokenPairIssuer {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final JwtTokenService jwtTokenService;
    private final SessionProperties sessionProperties;
    private final Clock clock;

    public TokenPairIssuer(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenGenerator refreshTokenGenerator,
            JwtTokenService jwtTokenService,
            SessionProperties sessionProperties,
            Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.jwtTokenService = jwtTokenService;
        this.sessionProperties = sessionProperties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TokenResponse issue(User user, AuthSession session, RefreshToken parentToken) {
        Instant now = clock.instant();
        Instant refreshExpiresAt = now.plus(sessionProperties.refreshTokenTtl());
        if (refreshExpiresAt.isAfter(session.getExpiresAt())) {
            refreshExpiresAt = session.getExpiresAt();
        }

        GeneratedRefreshToken generatedRefreshToken = refreshTokenGenerator.generate();
        RefreshToken refreshToken = new RefreshToken(
                session,
                parentToken,
                generatedRefreshToken.hash(),
                now,
                refreshExpiresAt
        );
        refreshTokenRepository.save(refreshToken);

        IssuedAccessToken accessToken = jwtTokenService.issueAccessToken(user, session.getId());
        return new TokenResponse(
                accessToken.value(),
                generatedRefreshToken.value(),
                "Bearer",
                accessToken.expiresAt(),
                refreshExpiresAt,
                session.getId()
        );
    }
}
