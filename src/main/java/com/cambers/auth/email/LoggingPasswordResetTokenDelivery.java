package com.cambers.auth.email;

import com.cambers.auth.config.properties.PasswordResetDeliveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.UUID;

@Component
@Profile({"local", "test"})
public class LoggingPasswordResetTokenDelivery implements PasswordResetTokenDelivery {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetTokenDelivery.class);

    private final PasswordResetDeliveryProperties properties;

    public LoggingPasswordResetTokenDelivery(PasswordResetDeliveryProperties properties) {
        this.properties = properties;
    }

    @Override
    public void deliver(String email, String rawToken, Instant expiresAt, UUID issuanceId) {
        log.debug(
                "Local password reset issuanceId={} expiresAt={} link={}",
                issuanceId,
                expiresAt,
                resetLink(rawToken)
        );
    }

    private String resetLink(String rawToken) {
        return UriComponentsBuilder.fromUri(properties.publicUrl())
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();
    }
}
