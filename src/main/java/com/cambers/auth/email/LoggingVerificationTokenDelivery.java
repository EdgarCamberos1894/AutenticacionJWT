package com.cambers.auth.email;

import com.cambers.auth.config.properties.VerificationDeliveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@Profile("!prod")
public class LoggingVerificationTokenDelivery implements VerificationTokenDelivery {

    private static final Logger log = LoggerFactory.getLogger(LoggingVerificationTokenDelivery.class);

    private final VerificationDeliveryProperties properties;

    public LoggingVerificationTokenDelivery(VerificationDeliveryProperties properties) {
        this.properties = properties;
    }

    @Override
    public void deliver(String email, String rawToken, Instant expiresAt) {
        log.debug(
                "Email verification for {} expires at {}: {}",
                email,
                expiresAt,
                verificationLink(rawToken)
        );
    }

    private String verificationLink(String rawToken) {
        String separator = properties.publicUrl().toString().contains("?") ? "&" : "?";
        return properties.publicUrl()
                + separator
                + "token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }
}
