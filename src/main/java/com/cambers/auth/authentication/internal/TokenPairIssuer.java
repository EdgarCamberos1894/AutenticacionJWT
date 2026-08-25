package com.cambers.auth.authentication.internal;

import com.cambers.auth.account.AuthenticatedAccount;
import com.cambers.auth.authentication.internal.model.AuthSession;
import com.cambers.auth.authentication.internal.model.RefreshToken;
import com.cambers.auth.authentication.internal.persistence.RefreshTokenRepository;
import com.cambers.auth.config.properties.SessionProperties;
import com.cambers.auth.dto.TokenPairResponse;
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
class TokenPairIssuer {
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final JwtTokenService jwtTokenService;
    private final SessionProperties sessionProperties;
    private final Clock clock;

    TokenPairIssuer(RefreshTokenRepository refreshTokenRepository, RefreshTokenGenerator refreshTokenGenerator,
            JwtTokenService jwtTokenService, SessionProperties sessionProperties, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.jwtTokenService = jwtTokenService;
        this.sessionProperties = sessionProperties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    TokenPairResponse issue(AuthenticatedAccount account, AuthSession session, RefreshToken parentToken) {
        Instant now = clock.instant();
        Instant refreshExpiresAt = now.plus(sessionProperties.refreshTokenTtl());
        if (refreshExpiresAt.isAfter(session.getExpiresAt())) refreshExpiresAt = session.getExpiresAt();
        GeneratedRefreshToken generated = refreshTokenGenerator.generate();
        refreshTokenRepository.save(new RefreshToken(session, parentToken, generated.hash(), now, refreshExpiresAt));
        IssuedAccessToken access = jwtTokenService.issueAccessToken(account, session.getId());
        return new TokenPairResponse(access.value(), generated.value(), "Bearer", access.expiresAt(),
                refreshExpiresAt, session.getId());
    }
}
