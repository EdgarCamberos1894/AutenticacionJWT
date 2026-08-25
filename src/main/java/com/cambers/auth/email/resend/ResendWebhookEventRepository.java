package com.cambers.auth.email.resend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ResendWebhookEventRepository extends JpaRepository<ResendWebhookEvent, String> {

    @Modifying
    @Query(value = """
            INSERT INTO resend_webhook_events (
                webhook_id,
                provider_message_id,
                event_type,
                event_created_at,
                received_at
            ) VALUES (
                :webhookId,
                :providerMessageId,
                :eventType,
                :eventCreatedAt,
                :receivedAt
            )
            ON CONFLICT (webhook_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("webhookId") String webhookId,
            @Param("providerMessageId") String providerMessageId,
            @Param("eventType") String eventType,
            @Param("eventCreatedAt") Instant eventCreatedAt,
            @Param("receivedAt") Instant receivedAt);

    Optional<ResendWebhookEvent> findTopByProviderMessageIdOrderByEventCreatedAtDesc(String providerMessageId);
}
