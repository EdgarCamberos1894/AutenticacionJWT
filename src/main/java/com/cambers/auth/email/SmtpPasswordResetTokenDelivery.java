package com.cambers.auth.email;

import com.cambers.auth.config.properties.AuthMailProperties;
import com.cambers.auth.config.properties.PasswordResetDeliveryProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@Profile("prod")
public class SmtpPasswordResetTokenDelivery implements PasswordResetTokenDelivery {

    private final JavaMailSender mailSender;
    private final PasswordResetDeliveryProperties properties;
    private final AuthMailProperties mailProperties;

    public SmtpPasswordResetTokenDelivery(
            JavaMailSender mailSender,
            PasswordResetDeliveryProperties properties,
            AuthMailProperties mailProperties) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.mailProperties = mailProperties;
    }

    @Override
    public void deliver(String email, String rawToken, Instant expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.from());
        message.setTo(email);
        message.setSubject("Reset your password");
        message.setText("Reset your password using this link:\n\n" + resetLink(rawToken)
                + "\n\nThis link expires at " + expiresAt + ".");
        mailSender.send(message);
    }

    private String resetLink(String rawToken) {
        String separator = properties.publicUrl().toString().contains("?") ? "&" : "?";
        return properties.publicUrl()
                + separator
                + "token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }
}
