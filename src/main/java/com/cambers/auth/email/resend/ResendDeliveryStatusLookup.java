package com.cambers.auth.email.resend;

import com.cambers.auth.email.outbox.EmailDeliveryStatusLookup;
import com.cambers.auth.email.outbox.EmailDeliveryStatusUpdate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Optional;

@Component
public class ResendDeliveryStatusLookup implements EmailDeliveryStatusLookup {

    private static final Comparator<EmailDeliveryStatusUpdate> EFFECTIVE_STATUS_ORDER =
            Comparator.comparingInt((EmailDeliveryStatusUpdate update) -> update.status().precedence())
                    .thenComparing(EmailDeliveryStatusUpdate::occurredAt);

    private final ResendWebhookEventRepository eventRepository;
    private final ResendEmailEventMapper eventMapper;

    public ResendDeliveryStatusLookup(
            ResendWebhookEventRepository eventRepository,
            ResendEmailEventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
    }

    @Override
    public Optional<EmailDeliveryStatusUpdate> findEffectiveStatus(String providerMessageId) {
        return eventRepository.findTop20ByProviderMessageIdAndEventTypeInOrderByEventCreatedAtDesc(
                        providerMessageId,
                        eventMapper.deliveryEventTypes()
                )
                .stream()
                .flatMap(event -> eventMapper.deliveryStatus(event.getEventType())
                        .map(status -> new EmailDeliveryStatusUpdate(
                                status,
                                event.getEventCreatedAt()
                        ))
                        .stream())
                .max(EFFECTIVE_STATUS_ORDER);
    }
}
