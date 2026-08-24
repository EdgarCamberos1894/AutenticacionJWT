package com.cambers.auth.service;

import com.cambers.auth.dto.TokenResponse;
import com.cambers.auth.entity.AuthSession;
import com.cambers.auth.entity.RefreshToken;
import com.cambers.auth.entity.User;
import com.cambers.auth.exception.ProblemCode;
import com.cambers.auth.exception.UnauthorizedException;
import com.cambers.auth.repository.RefreshTokenRepository;
import com.cambers.auth.security.refresh.RefreshTokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class RefreshTokenRotationService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final TokenPairIssuer tokenPairIssuer;
    private final Clock clock;

    public RefreshTokenRotationService(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenGenerator refreshTokenGenerator,
            TokenPairIssuer tokenPairIssuer,
            Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.tokenPairIssuer = tokenPairIssuer;
        this.clock = clock;
    }

    @Transactional
    public TokenResponse rotate(String rawRefreshToken) {
        String tokenHash = refreshTokenGenerator.hash(rawRefreshToken);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidRefreshToken);

        AuthSession session = refreshToken.getSession();
        Instant now = clock.instant();

        if (refreshToken.isUsed()) {
            throw new RefreshTokenReuseDetectedException(session.getId());
        }

        if (refreshToken.isRevoked()) {
            if (!session.isRevoked()) {
                throw new RefreshTokenReuseDetectedException(session.getId());
            }
            throw invalidRefreshToken();
        }

        if (refreshToken.isExpired(now) || session.isExpired(now) || session.isRevoked()) {
            throw invalidRefreshToken();
        }

        User user = session.getUser();
        if (!user.isAuthenticationAllowed()) {
            throw invalidRefreshToken();
        }

        refreshToken.markUsed(now);
        session.touch(now);
        return tokenPairIssuer.issue(user, session, refreshToken);
    }

    private UnauthorizedException invalidRefreshToken() {
        return new UnauthorizedException(
                ProblemCode.INVALID_REFRESH_TOKEN,
                "The refresh token is invalid or expired."
        );
    }
}
