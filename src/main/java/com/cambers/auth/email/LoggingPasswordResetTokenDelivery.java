package com.cambers.auth.email;

import com.cambers.auth.config.properties.PasswordResetDeliveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@Profile("!prod")
public class LoggingPasswordResetTokenDelivery implements PasswordResetTokenDelivery {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetTokenDelivery.class);

    private final PasswordResetDeliveryProperties properties;

    public LoggingPasswordResetTokenDelivery(PasswordResetDeliveryProperties properties) {
        this.properties = properties;
    }

    @Override
    public void deliver(String email, String rawToken, Instant expiresAt) {
        log.debug("Password reset for {} expires at {}: {}", email, expiresAt, resetLink(rawToken));
    }

    private String resetLink(String rawToken) {
        String separator = properties.publicUrl().toString().contains("?") ? "&" : "?";
        return properties.publicUrl()
                + separator
                + "token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }
}
