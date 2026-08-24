package com.cambers.auth.repository;

import com.cambers.auth.entity.AuthSession;
import com.cambers.auth.entity.SessionRevocationReason;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    @Query("""
            select session
            from AuthSession session
            where session.user.id = :userId
              and session.revokedAt is null
              and session.expiresAt > :now
            order by session.createdAt desc
            """)
    List<AuthSession> findActiveByUserId(
            @Param("userId") UUID userId,
            @Param("now") Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSession session where session.id = :sessionId")
    Optional<AuthSession> findByIdForUpdate(@Param("sessionId") UUID sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from AuthSession session
            where session.id = :sessionId
              and session.user.id = :userId
            """)
    Optional<AuthSession> findByIdAndUserIdForUpdate(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuthSession session
            set session.revokedAt = :revokedAt,
                session.revocationReason = :reason
            where session.user.id = :userId
              and session.revokedAt is null
            """)
    int revokeAllActiveByUserId(
            @Param("userId") UUID userId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") SessionRevocationReason reason
    );
}
