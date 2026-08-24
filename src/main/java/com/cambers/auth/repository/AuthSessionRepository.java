package com.cambers.auth.repository;

import com.cambers.auth.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    List<AuthSession> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<AuthSession> findByIdAndUserId(UUID id, UUID userId);
}
