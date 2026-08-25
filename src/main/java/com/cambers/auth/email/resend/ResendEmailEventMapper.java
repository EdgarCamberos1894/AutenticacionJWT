package com.cambers.auth.email.resend;

import com.cambers.auth.email.outbox.EmailDeliveryStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ResendEmailEventMapper {

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
}
