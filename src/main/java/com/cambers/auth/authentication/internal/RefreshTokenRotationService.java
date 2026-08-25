package com.cambers.auth.authentication.internal;

import com.cambers.auth.account.AccountAuthentication;
import com.cambers.auth.account.AuthenticatedAccount;
import com.cambers.auth.authentication.internal.model.*;
import com.cambers.auth.authentication.internal.persistence.RefreshTokenRepository;
import com.cambers.auth.dto.TokenPairResponse;
import com.cambers.auth.exception.ProblemCode;
import com.cambers.auth.exception.UnauthorizedException;
import com.cambers.auth.security.refresh.RefreshTokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;

@Service
class RefreshTokenRotationService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final TokenPairIssuer tokenPairIssuer;
    private final AccountAuthentication accountAuthentication;
    private final Clock clock;

    RefreshTokenRotationService(RefreshTokenRepository refreshTokenRepository, RefreshTokenGenerator refreshTokenGenerator,
            TokenPairIssuer tokenPairIssuer, AccountAuthentication accountAuthentication, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.tokenPairIssuer = tokenPairIssuer;
        this.accountAuthentication = accountAuthentication;
        this.clock = clock;
    }

    @Transactional
    TokenPairResponse rotate(String rawRefreshToken) {
        String tokenHash = refreshTokenGenerator.hash(rawRefreshToken);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidRefreshToken);
        AuthSession session = refreshToken.getSession();
        Instant now = clock.instant();
        if (refreshToken.isUsed()) throw new RefreshTokenReuseDetectedException(session.getId());
        if (refreshToken.isRevoked()) {
            if (!session.isRevoked()) throw new RefreshTokenReuseDetectedException(session.getId());
            throw invalidRefreshToken();
        }
        if (refreshToken.isExpired(now) || session.isExpired(now) || session.isRevoked()) throw invalidRefreshToken();
        AuthenticatedAccount account = accountAuthentication.findAuthenticationAllowed(session.getAccountId())
                .orElseThrow(this::invalidRefreshToken);
        refreshToken.markUsed(now);
        session.touch(now);
        return tokenPairIssuer.issue(account, session, refreshToken);
    }

    private UnauthorizedException invalidRefreshToken() {
        return new UnauthorizedException(ProblemCode.INVALID_REFRESH_TOKEN, "The refresh token is invalid or expired.");
    }
}
