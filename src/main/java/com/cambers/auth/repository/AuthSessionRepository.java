package com.cambers.auth.repository;

import com.cambers.auth.entity.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    List<AuthSession> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<AuthSession> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSession session where session.id = :sessionId")
    Optional<AuthSession> findByIdForUpdate(@Param("sessionId") UUID sessionId);
}
