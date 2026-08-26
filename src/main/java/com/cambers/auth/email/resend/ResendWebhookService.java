package com.cambers.auth.email.resend;

import com.cambers.auth.email.outbox.EmailOutboxRepository;
import com.cambers.auth.platform.BadRequestException;
import com.cambers.auth.platform.ProblemCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;

@Service
public class ResendWebhookService {

    private final ResendWebhookEventRepository eventRepository;
    private final EmailOutboxRepository outboxRepository;
    private final ResendEmailEventMapper eventMapper;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public ResendWebhookService(
            ResendWebhookEventRepository eventRepository,
            EmailOutboxRepository outboxRepository,
            ResendEmailEventMapper eventMapper,
            JsonMapper jsonMapper,
            Clock clock) {
        this.eventRepository = eventRepository;
        this.outboxRepository = outboxRepository;
        this.eventMapper = eventMapper;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public void process(String webhookId, byte[] rawBody) {
        ResendWebhookEnvelope envelope = parse(rawBody);
        Instant receivedAt = clock.instant();
        Instant eventCreatedAt = parseEventTimestamp(envelope.created_at());
        String providerMessageId = envelope.data() == null ? null : blankToNull(envelope.data().email_id());

        int inserted = eventRepository.insertIfAbsent(
                webhookId,
                providerMessageId,
                envelope.type(),
                eventCreatedAt,
                receivedAt
        );
        if (inserted == 0 || providerMessageId == null) {
            return;
        }

        eventMapper.deliveryStatus(envelope.type()).ifPresent(status ->
                outboxRepository.findByProviderMessageIdForUpdate(providerMessageId)
                        .ifPresent(message -> message.applyDeliveryStatus(status, eventCreatedAt, receivedAt))
        );
    }

    private ResendWebhookEnvelope parse(byte[] rawBody) {
        try {
            ResendWebhookEnvelope envelope = jsonMapper.readValue(rawBody, ResendWebhookEnvelope.class);
            if (envelope == null || envelope.type() == null || envelope.type().isBlank()
                    || envelope.created_at() == null || envelope.created_at().isBlank()) {
                throw invalidPayload();
            }
            return envelope;
        } catch (JacksonException exception) {
            throw invalidPayload();
        }
    }

    private Instant parseEventTimestamp(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw invalidPayload();
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private BadRequestException invalidPayload() {
        return new BadRequestException(
                ProblemCode.INVALID_WEBHOOK_PAYLOAD,
                "The webhook payload is invalid."
        );
    }

    record ResendWebhookEnvelope(String type, String created_at, ResendWebhookData data) {
    }

    record ResendWebhookData(String email_id) {
    }
}
