package com.cambers.auth.authentication.internal.persistence;

import com.cambers.auth.authentication.internal.model.AuthSession;
import com.cambers.auth.authentication.internal.model.SessionRevocationReason;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    @Query("select session from AuthSession session where session.accountId = :accountId and session.revokedAt is null and session.expiresAt > :now order by session.createdAt desc")
    List<AuthSession> findActiveByAccountId(@Param("accountId") UUID accountId, @Param("now") Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select session from AuthSession session where session.id = :sessionId")
    Optional<AuthSession> findByIdForUpdate(@Param("sessionId") UUID sessionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select session from AuthSession session where session.id = :sessionId and session.accountId = :accountId")
    Optional<AuthSession> findByIdAndAccountIdForUpdate(@Param("sessionId") UUID sessionId, @Param("accountId") UUID accountId);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthSession session set session.revokedAt = :revokedAt, session.revocationReason = :reason where session.accountId = :accountId and session.revokedAt is null")
    int revokeAllActiveByAccountId(@Param("accountId") UUID accountId, @Param("revokedAt") Instant revokedAt, @Param("reason") SessionRevocationReason reason);
}
