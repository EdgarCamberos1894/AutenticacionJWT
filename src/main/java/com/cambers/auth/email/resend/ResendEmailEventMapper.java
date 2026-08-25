package com.cambers.auth.email.resend;

import com.cambers.auth.email.outbox.EmailDeliveryStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

@Component
public class ResendEmailEventMapper {

    private static final Set<String> DELIVERY_EVENT_TYPES = Set.of(
            "email.sent",
            "email.delivered",
            "email.delivery_delayed",
            "email.bounced",
            "email.complained",
            "email.failed",
            "email.suppressed"
    );

    public Optional<EmailDeliveryStatus> deliveryStatus(String eventType) {
        if (eventType == null) {
            return Optional.empty();
        }
        return switch (eventType) {
            case "email.sent" -> Optional.of(EmailDeliveryStatus.ACCEPTED);
            case "email.delivered" -> Optional.of(EmailDeliveryStatus.DELIVERED);
            case "email.delivery_delayed" -> Optional.of(EmailDeliveryStatus.DELAYED);
            case "email.bounced" -> Optional.of(EmailDeliveryStatus.BOUNCED);
            case "email.complained" -> Optional.of(EmailDeliveryStatus.COMPLAINED);
            case "email.failed" -> Optional.of(EmailDeliveryStatus.FAILED);
            case "email.suppressed" -> Optional.of(EmailDeliveryStatus.SUPPRESSED);
            default -> Optional.empty();
        };
    }

    public Set<String> deliveryEventTypes() {
        return DELIVERY_EVENT_TYPES;
    }
}
