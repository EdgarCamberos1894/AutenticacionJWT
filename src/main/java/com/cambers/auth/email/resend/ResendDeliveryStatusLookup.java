package com.cambers.auth.email.resend;

import com.cambers.auth.email.outbox.EmailDeliveryStatusLookup;
import com.cambers.auth.email.outbox.EmailDeliveryStatusUpdate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ResendDeliveryStatusLookup implements EmailDeliveryStatusLookup {

    private final ResendWebhookEventRepository eventRepository;
    private final ResendEmailEventMapper eventMapper;

    public ResendDeliveryStatusLookup(
            ResendWebhookEventRepository eventRepository,
            ResendEmailEventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
    }

    @Override
    public Optional<EmailDeliveryStatusUpdate> findLatest(String providerMessageId) {
        return eventRepository.findTop20ByProviderMessageIdOrderByEventCreatedAtDesc(providerMessageId)
                .stream()
                .flatMap(event -> eventMapper.deliveryStatus(event.getEventType())
                        .map(status -> new EmailDeliveryStatusUpdate(
                                status,
                                event.getEventCreatedAt()
                        ))
                        .stream())
                .findFirst();
    }
}
