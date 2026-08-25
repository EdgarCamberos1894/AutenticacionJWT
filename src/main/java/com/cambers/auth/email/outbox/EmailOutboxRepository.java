package com.cambers.auth.email.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxMessage, UUID> {

    @Query(value = """
            SELECT id
            FROM email_outbox_messages
            WHERE expires_at > :now
              AND (
                    (status = 'PENDING' AND next_attempt_at <= :now)
                 OR (status = 'PROCESSING' AND locked_at < :leaseExpiredBefore)
              )
            ORDER BY next_attempt_at, created_at
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findNextClaimableId(
            @Param("now") Instant now,
            @Param("leaseExpiredBefore") Instant leaseExpiredBefore);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from EmailOutboxMessage message where message.providerMessageId = :providerMessageId")
    Optional<EmailOutboxMessage> findByProviderMessageIdForUpdate(
            @Param("providerMessageId") String providerMessageId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailOutboxMessage message
               set message.status = com.cambers.auth.email.outbox.EmailOutboxStatus.DEAD,
                   message.deliveryStatus = com.cambers.auth.email.outbox.EmailDeliveryStatus.FAILED,
                   message.lastErrorCode = 'TOKEN_EXPIRED',
                   message.lockedAt = null,
                   message.lockedBy = null,
                   message.updatedAt = :now
             where message.status in (
                    com.cambers.auth.email.outbox.EmailOutboxStatus.PENDING,
                    com.cambers.auth.email.outbox.EmailOutboxStatus.PROCESSING)
               and message.expiresAt <= :now
            """)
    int expireMessages(@Param("now") Instant now);
}
