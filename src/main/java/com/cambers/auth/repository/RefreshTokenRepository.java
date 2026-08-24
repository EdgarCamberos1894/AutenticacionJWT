package com.cambers.auth.repository;

import com.cambers.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select distinct token
            from RefreshToken token
            join fetch token.session session
            join fetch session.user user
            left join fetch user.roles
            where token.tokenHash = :tokenHash
            """)
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken token
            set token.revokedAt = :revokedAt
            where token.session.id = :sessionId
              and token.revokedAt is null
            """)
    int revokeAllBySessionId(
            @Param("sessionId") UUID sessionId,
            @Param("revokedAt") Instant revokedAt
    );
}
