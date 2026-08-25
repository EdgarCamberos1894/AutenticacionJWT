package com.cambers.auth.account.internal.persistence;

import com.cambers.auth.account.internal.model.OneTimeToken;
import com.cambers.auth.account.internal.model.TokenPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, UUID> {
    @Query("select token.user.id from OneTimeToken token where token.tokenHash = :tokenHash")
    Optional<UUID> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);
    @Query("select token.id from OneTimeToken token where token.user.id = :userId and token.purpose = :purpose and token.consumedAt is null and token.invalidatedAt is null")
    Optional<UUID> findActiveTokenId(@Param("userId") UUID userId, @Param("purpose") TokenPurpose purpose);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from OneTimeToken token join fetch token.user user where token.tokenHash = :tokenHash")
    Optional<OneTimeToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
    @Modifying(flushAutomatically = true)
    @Query("update OneTimeToken token set token.invalidatedAt = :now where token.user.id = :userId and token.purpose = :purpose and token.consumedAt is null and token.invalidatedAt is null")
    int invalidateActiveTokens(@Param("userId") UUID userId, @Param("purpose") TokenPurpose purpose, @Param("now") Instant now);
    @Modifying(flushAutomatically = true)
    @Query("update OneTimeToken token set token.invalidatedAt = :now where token.user.id = :userId and token.purpose = :purpose and token.id <> :consumedTokenId and token.consumedAt is null and token.invalidatedAt is null")
    int invalidateOtherActiveTokens(@Param("userId") UUID userId, @Param("purpose") TokenPurpose purpose, @Param("consumedTokenId") UUID consumedTokenId, @Param("now") Instant now);
}
