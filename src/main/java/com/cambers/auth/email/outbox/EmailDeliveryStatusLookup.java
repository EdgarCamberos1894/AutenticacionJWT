package com.cambers.auth.email.outbox;

import java.util.Optional;

public interface EmailDeliveryStatusLookup {

    Optional<EmailDeliveryStatusUpdate> findEffectiveStatus(String providerMessageId);
}
