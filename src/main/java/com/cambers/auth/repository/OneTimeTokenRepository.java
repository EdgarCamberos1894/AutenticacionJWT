package com.cambers.auth.repository;

import com.cambers.auth.entity.OneTimeToken;
import com.cambers.auth.enums.TokenPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from OneTimeToken token
            join fetch token.user user
            where token.tokenHash = :tokenHash
            """)
    Optional<OneTimeToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            update OneTimeToken token
            set token.consumedAt = :now
            where token.user.id = :userId
              and token.purpose = :purpose
              and token.consumedAt is null
            """)
    int consumeActiveTokens(
            @Param("userId") UUID userId,
            @Param("purpose") TokenPurpose purpose,
            @Param("now") Instant now
    );
}
