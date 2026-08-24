package com.cambers.auth.email;

import com.cambers.auth.config.properties.VerificationDeliveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.UUID;

@Component
@Profile({"local", "test"})
public class LoggingVerificationTokenDelivery implements VerificationTokenDelivery {

    private static final Logger log = LoggerFactory.getLogger(LoggingVerificationTokenDelivery.class);

    private final VerificationDeliveryProperties properties;

    public LoggingVerificationTokenDelivery(VerificationDeliveryProperties properties) {
        this.properties = properties;
    }

    @Override
    public void deliver(String email, String rawToken, Instant expiresAt, UUID issuanceId) {
        log.debug(
                "Local email verification issuanceId={} expiresAt={} link={}",
                issuanceId,
                expiresAt,
                verificationLink(rawToken)
        );
    }

    private String verificationLink(String rawToken) {
        return UriComponentsBuilder.fromUri(properties.publicUrl())
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();
    }
}
